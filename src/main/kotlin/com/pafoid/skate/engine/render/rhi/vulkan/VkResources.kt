package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.render.rhi.BufferDesc
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RenderTargetDesc
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.RhiVertexLayout
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.ShaderDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.VertexLayoutDesc
import com.pafoid.skate.engine.render.rhi.Wrap
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferCopy
import org.lwjgl.vulkan.VkBufferImageCopy
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkFormatProperties
import org.lwjgl.vulkan.VkImageBlit
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkImageMemoryBarrier
import org.lwjgl.vulkan.VkImageViewCreateInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Everything the Vulkan backend allocates (VK-02): the VMA allocator ([VkAllocator]), shared samplers and descriptor
 * set layouts, per-frame-in-flight transient resources ([frames]), the dummies standing in for unbound inputs, and the
 * `create*` bodies of [VulkanDevice]. Uploads go through host staging buffers and one-shot copies, so they belong at
 * load time or between frames (a one-shot runs before the commands already recorded in an open frame).
 */
internal class VkResourceFactory(val core: VkCore, framesInFlight: Int = 1) : AutoCloseable {
    val allocator = VkAllocator(core)
    val samplers = VkSamplerCache(core)
    val layouts = DescriptorLayoutCache(core)
    val frames = VkFrameResources(core, allocator, framesInFlight)
    private val blitFilters = HashMap<Int, Int>()

    val dummyFloatVertex: VkAllocBuffer = allocator.buffer(16, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, MemoryKind.HOST).also {
        MemoryUtil.memByteBuffer(it.address, 16).order(ByteOrder.nativeOrder()).putFloat(0f).putFloat(0f).putFloat(0f).putFloat(1f)
    }
    val dummyIntVertex: VkAllocBuffer = allocator.buffer(16, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, MemoryKind.HOST).also {
        MemoryUtil.memSet(it.address, 0, 16)
    }
    /** Indexed by `dim.ordinal * 2 + depth` (no key allocation per lookup). */
    private val dummies = arrayOfNulls<VkRhiTexture>(SpirvReflection.ImageDim.entries.size * 2)

    fun createBuffer(desc: BufferDesc): RhiBuffer = VkRhiBuffer(this, desc.usage, desc.update, desc.size)

    fun createBuffer(usage: BufferUsage, update: BufferUpdate, data: FloatArray): RhiBuffer =
        VkRhiBuffer(this, usage, update, maxOf(data.size * 4L, 4L)).also { if (data.isNotEmpty()) it.update(0, data) }

    fun createBuffer(usage: BufferUsage, update: BufferUpdate, data: IntArray): RhiBuffer =
        VkRhiBuffer(this, usage, update, maxOf(data.size * 4L, 4L)).also { it.replace(data) }

    fun createVertexLayout(desc: VertexLayoutDesc): RhiVertexLayout = VkRhiVertexLayout(desc)

    fun createVertexInput(layout: RhiVertexLayout, vertexBuffers: List<RhiBuffer>, indexBuffer: RhiBuffer?): RhiVertexInput =
        VkRhiVertexInput(layout as VkRhiVertexLayout, vertexBuffers.map { it as VkRhiBuffer }, indexBuffer as VkRhiBuffer?)

    fun createTexture(desc: TextureDesc): RhiTexture = VkRhiTexture(this, desc)

    fun createRenderTarget(desc: RenderTargetDesc): RhiRenderTarget = VkRhiRenderTarget(this, desc)

    fun createShader(desc: ShaderDesc) = VkRhiShader(core, layouts, desc)

    /** Texture bound where a sampler's unit holds nothing usable: opaque black (GL's unbound result), or far depth. */
    fun dummyFor(d: SpirvReflection.Descriptor): VkRhiTexture {
        val index = d.dim!!.ordinal * 2 + if (d.depth) 1 else 0
        return dummies[index] ?: createDummy(d).also { dummies[index] = it }
    }

    private fun createDummy(d: SpirvReflection.Descriptor): VkRhiTexture {
        val type = when (d.dim!!) {
            SpirvReflection.ImageDim.D2 -> TextureType.TEXTURE_2D
            SpirvReflection.ImageDim.D2_ARRAY -> TextureType.TEXTURE_2D_ARRAY
            SpirvReflection.ImageDim.CUBE -> TextureType.CUBE
        }
        val layers = if (type == TextureType.CUBE) 6 else 1
        val format = if (d.depth) PixelFormat.DEPTH32F else PixelFormat.RGBA8
        val sampler = SamplerDesc(Filter.NEAREST, Filter.NEAREST, Wrap.CLAMP_TO_EDGE, compare = if (d.depth) CompareFunc.LESS_EQUAL else null)
        return VkRhiTexture(this, TextureDesc(type, format, 1, 1, layers, sampler = sampler), attachment = d.depth).also { t ->
            val texel = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN)
            if (d.depth) texel.putFloat(1f) else texel.put(0).put(0).put(0).put(-1)
            texel.flip()
            for (layer in 0 until layers) t.upload(0, layer, texel.duplicate())
        }
    }

    /** Filter for mip-generation blits of [format]: linear when the format supports it, else nearest. */
    fun blitFilter(format: Int): Int = blitFilters.getOrPut(format) {
        stackPush().use { s ->
            val props = VkFormatProperties.malloc(s)
            vkGetPhysicalDeviceFormatProperties(core.physical, format, props)
            val features = props.optimalTilingFeatures()
            check(features and VK_FORMAT_FEATURE_BLIT_SRC_BIT != 0 && features and VK_FORMAT_FEATURE_BLIT_DST_BIT != 0) {
                "Format $format cannot be blitted: no mip generation"
            }
            if (features and VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT != 0) VK_FILTER_LINEAR else VK_FILTER_NEAREST
        }
    }

    /** A mapped host buffer holding [bytes], for one upload; closed by the caller after the copy. */
    fun staging(bytes: ByteBuffer): VkAllocBuffer {
        val n = bytes.remaining()
        val b = allocator.buffer(n.toLong(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT, MemoryKind.HOST)
        MemoryUtil.memCopy(MemoryUtil.memAddress(bytes), b.address, n.toLong())
        return b
    }

    override fun close() {
        dummies.forEach { it?.close() }
        dummies.fill(null)
        dummyFloatVertex.close()
        dummyIntVertex.close()
        frames.close()
        layouts.close()
        samplers.close()
        allocator.close()
    }
}

/**
 * An RHI buffer on Vulkan, by [usage] and [update]:
 * - uniform: a CPU copy only; each draw snapshots it into the frame's uniform ring.
 * - static vertex/index: device-local memory, filled through staging copies.
 * - dynamic/stream vertex/index/instance: a CPU shadow, versioned per write. The first draw after a write pushes it into
 *   the current frame's vertex ring ([binding]), so writes between draws and frames in flight never race the GPU.
 */
internal class VkRhiBuffer(
    private val factory: VkResourceFactory,
    override val usage: BufferUsage,
    override val update: BufferUpdate,
    size: Long,
) : RhiBuffer {
    override var size: Long = size
        private set

    private val mode = when {
        usage == BufferUsage.UNIFORM -> Mode.UNIFORM
        update == BufferUpdate.STATIC -> Mode.DEVICE
        else -> Mode.VERSIONED
    }

    private enum class Mode { UNIFORM, DEVICE, VERSIONED }

    /** CPU contents of a uniform buffer. */
    var contents: ByteBuffer = ByteBuffer.allocate(if (mode == Mode.UNIFORM) size.toInt().coerceAtLeast(16) else 0).order(ByteOrder.LITTLE_ENDIAN)
        private set

    private var device: VkAllocBuffer? = if (mode == Mode.DEVICE) deviceBuffer(size) else null
    private var shadow: ByteBuffer? = if (mode == Mode.VERSIONED) MemoryUtil.memCalloc(size.toInt().coerceAtLeast(16)) else null
    private val versions = BufferVersions()

    /** GPU copies pushed so far (a dynamic buffer's versions; tests and stats). */
    val versionPushes: Int get() = versions.pushes

    private fun deviceBuffer(size: Long) = factory.allocator.buffer(
        size,
        VK_BUFFER_USAGE_TRANSFER_DST_BIT or if (usage == BufferUsage.INDEX) VK_BUFFER_USAGE_INDEX_BUFFER_BIT else VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
        MemoryKind.DEVICE,
    )

    /** VkBuffer and offset to bind for a draw recorded now, set by [resolve] (no allocation per draw). */
    val boundBuffer: Long get() = if (mode == Mode.DEVICE) device!!.buffer else versions.buffer
    val boundOffset: Long get() = if (mode == Mode.DEVICE) 0L else versions.offset

    /** Makes [boundBuffer]/[boundOffset] valid for a draw recorded now: a dynamic buffer pushes its current version. */
    fun resolve(frames: VkFrameResources) {
        when (mode) {
            Mode.DEVICE -> {}
            Mode.VERSIONED -> versions.resolve(frames.serial) { size ->
                val offset = frames.pushVertexData(shadow!!, size)
                versions.record(frames.serial, frames.lastVertexBuffer, offset)
            }
            Mode.UNIFORM -> error("Uniform buffers are bound through descriptors")
        }
    }

    private fun write(offset: Long, src: ByteBuffer) {
        val n = src.remaining()
        require(offset + n <= size) { "Write of $n bytes at $offset overflows a $size-byte buffer" }
        when (mode) {
            Mode.UNIFORM -> contents.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(offset.toInt()).let { (it as ByteBuffer).put(src.duplicate()) }
            Mode.VERSIONED -> {
                MemoryUtil.memCopy(MemoryUtil.memAddress(src), MemoryUtil.memAddress(shadow!!) + offset, n.toLong())
                versions.written(offset, n.toLong())
            }
            Mode.DEVICE -> {
                val staging = factory.staging(src)
                try {
                    factory.core.oneShot { cmd ->
                        stackPush().use { s ->
                            val region = VkBufferCopy.calloc(1, s)
                            region[0].srcOffset(0).dstOffset(offset).size(n.toLong())
                            vkCmdCopyBuffer(cmd, staging.buffer, device!!.buffer, region)
                        }
                    }
                } finally {
                    staging.close()
                }
            }
        }
    }

    /** Fills a temporary native buffer of [n] bytes with [fill] and writes it at [offset]. */
    private inline fun writeVia(offset: Long, n: Int, fill: (ByteBuffer) -> Unit) {
        val b = MemoryUtil.memAlloc(maxOf(n, 1)).order(ByteOrder.nativeOrder())
        try {
            fill(b)
            b.flip()
            write(offset, b)
        } finally {
            MemoryUtil.memFree(b)
        }
    }

    override fun update(offset: Long, data: FloatArray) =
        writeVia(offset, data.size * 4) { it.asFloatBuffer().put(data); it.position(data.size * 4) }

    override fun update(offset: Long, data: FloatBuffer) {
        val n = data.remaining() * 4
        if (data.isDirect) write(offset, MemoryUtil.memByteBuffer(MemoryUtil.memAddress(data), n))
        else writeVia(offset, n) { it.asFloatBuffer().put(data.duplicate()); it.position(n) }
    }

    override fun update(offset: Long, data: ByteBuffer) {
        if (data.isDirect) write(offset, data) else writeVia(offset, data.remaining()) { it.put(data.duplicate()) }
    }

    override fun resize(size: Long) {
        this.size = size
        when (mode) {
            Mode.UNIFORM -> contents = ByteBuffer.allocate(size.toInt().coerceAtLeast(16)).order(ByteOrder.LITTLE_ENDIAN)
            Mode.DEVICE -> {
                val old = device
                device = deviceBuffer(size)
                old?.let { factory.core.destroyLaterOrNow { it.close() } }
            }
            Mode.VERSIONED -> {
                shadow?.let { MemoryUtil.memFree(it) }
                shadow = MemoryUtil.memCalloc(size.toInt().coerceAtLeast(16))
                versions.resized()
            }
        }
    }

    override fun replace(data: FloatArray) {
        resize(maxOf(data.size * 4L, 4L))
        if (data.isNotEmpty()) update(0, data)
    }

    override fun replace(data: IntArray) {
        resize(maxOf(data.size * 4L, 4L))
        if (data.isEmpty()) return
        writeVia(0, data.size * 4) { it.asIntBuffer().put(data); it.position(data.size * 4) }
    }

    override fun close() {
        device?.let { d -> factory.core.destroyLaterOrNow { d.close() } }
        device = null
        shadow?.let { MemoryUtil.memFree(it) }
        shadow = null
    }
}

/**
 * Version bookkeeping of a dynamic buffer (pure, unit-tested): the GPU copy pushed for the current frame stays valid
 * until the buffer is written again or a new frame starts (its ring slot may be reused), and only the bytes written
 * since the last resize are pushed.
 */
class BufferVersions {
    /** Bytes written since the last resize. */
    var extent = 0L
        private set
    private var pushedSerial = -1L

    /** The pushed copy's VkBuffer and offset. */
    var buffer = 0L
        private set
    var offset = 0L
        private set

    /** Pushes counted so far (tests and stats). */
    var pushes = 0
        private set

    fun written(offset: Long, length: Long) {
        extent = maxOf(extent, offset + length)
        pushedSerial = -1
    }

    fun resized() {
        extent = 0
        pushedSerial = -1
    }

    /**
     * Makes [buffer]/[offset] valid for a draw in frame [serial]: when the copy is stale, [push] copies the first n
     * bytes and calls [record].
     */
    inline fun resolve(serial: Long, push: (Long) -> Unit) {
        if (!isCurrent(serial)) push(maxOf(extent, 4L))
    }

    fun isCurrent(serial: Long) = pushedSerial == serial

    fun record(serial: Long, buffer: Long, offset: Long) {
        this.buffer = buffer
        this.offset = offset
        pushedSerial = serial
        pushes++
    }
}

internal class VkRhiVertexLayout(override val desc: VertexLayoutDesc) : RhiVertexLayout {
    override fun close() {}
}

internal class VkRhiVertexInput(
    override val layout: VkRhiVertexLayout,
    val vertexBuffers: List<VkRhiBuffer>,
    val indexBuffer: VkRhiBuffer?,
) : RhiVertexInput {
    override fun close() {}
}

/**
 * A sampled image: 2D, 2D array or cube, colour or depth (depth-compare samplers for shadow maps). Rests in the layout
 * shaders read it in ([sampledLayout]); uploads, mip generation, render passes and readback transition away and back.
 * Its sampler comes from the shared [VkSamplerCache].
 */
internal class VkRhiTexture(
    private val factory: VkResourceFactory,
    override val desc: TextureDesc,
    attachment: Boolean = false,
) : RhiTexture {
    private val core = factory.core
    val format = VkTranslate.imageFormat(desc.format)
    val isDepth = desc.format.isDepth
    val aspect = if (isDepth) VK_IMAGE_ASPECT_DEPTH_BIT else VK_IMAGE_ASPECT_COLOR_BIT
    val sampledLayout = if (isDepth) VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL else VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
    private val allocation: VkAllocImage
    val image: Long
    val view: Long
    private val layerViews = HashMap<Int, Long>()
    private var closed = false

    override var sampler: SamplerDesc = desc.sampler
        private set

    /** The shared `VkSampler` for [sampler]. */
    var vkSampler: Long = factory.samplers.sampler(desc.sampler)
        private set

    override val nativeHandle: Long get() = image

    init {
        allocation = stackPush().use { s ->
            var usage = VK_IMAGE_USAGE_SAMPLED_BIT or VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_TRANSFER_SRC_BIT
            if (attachment) usage = usage or if (isDepth) VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT else VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
            val info = VkImageCreateInfo.calloc(s).`sType$Default`()
                .imageType(VK_IMAGE_TYPE_2D).format(format)
                .extent { it.width(desc.width).height(desc.height).depth(1) }
                .mipLevels(desc.mipLevels).arrayLayers(desc.layers)
                .samples(VK_SAMPLE_COUNT_1_BIT).tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            if (desc.type == TextureType.CUBE) info.flags(VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT)
            factory.allocator.image(info)
        }
        image = allocation.image
        view = createView(viewType(desc.type), 0, desc.layers, desc.mipLevels)
        // Start in the sampled layout so a texture never uploaded still reads (as zeros) without a validation error
        core.oneShot { cmd -> transition(cmd, VK_IMAGE_LAYOUT_UNDEFINED, sampledLayout) }
    }

    private fun createView(type: Int, baseLayer: Int, layers: Int, levels: Int): Long = stackPush().use { s ->
        val info = VkImageViewCreateInfo.calloc(s).`sType$Default`().image(image).viewType(type).format(format)
            .subresourceRange { it.aspectMask(aspect).baseMipLevel(0).levelCount(levels).baseArrayLayer(baseLayer).layerCount(layers) }
        val p = s.mallocLong(1)
        vk(vkCreateImageView(core.device, info, null, p), "vkCreateImageView")
        p[0]
    }

    /** Single-layer, single-level 2D view for rendering into [layer] of a layered target. */
    fun layerView(layer: Int): Long = if (desc.layers == 1 && desc.type == TextureType.TEXTURE_2D && desc.mipLevels == 1) view
        else layerViews.getOrPut(layer) { createView(VK_IMAGE_VIEW_TYPE_2D, layer, 1, 1) }

    override fun setSampler(sampler: SamplerDesc) {
        if (sampler == this.sampler) return
        this.sampler = sampler
        vkSampler = factory.samplers.sampler(sampler)
    }

    fun transition(cmd: VkCommandBuffer, from: Int, to: Int, baseLevel: Int = 0, levels: Int = desc.mipLevels) = stackPush().use { s ->
        val barrier = VkImageMemoryBarrier.calloc(1, s)
        barrier[0].`sType$Default`().oldLayout(from).newLayout(to)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).image(image)
            .subresourceRange { it.aspectMask(aspect).baseMipLevel(baseLevel).levelCount(levels).baseArrayLayer(0).layerCount(desc.layers) }
            .srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT).dstAccessMask(VK_ACCESS_MEMORY_READ_BIT or VK_ACCESS_MEMORY_WRITE_BIT)
        vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barrier)
    }

    /** Texel bytes of [level] of [layer] as the image stores them (three-channel formats widened to four). */
    private fun imageBytes(data: ByteBuffer): ByteBuffer = when (desc.format) {
        PixelFormat.RGB8, PixelFormat.RGB32F -> {
            val bytes = ByteArray(data.remaining()).also { data.duplicate().get(it) }
            val wide = if (desc.format == PixelFormat.RGB8) VkTranslate.widenToFour(bytes, 1, byteArrayOf(-1))
                else VkTranslate.widenToFour(bytes, 4, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(1f).array())
            ByteBuffer.allocateDirect(wide.size).put(wide).flip()
        }
        else -> if (data.isDirect) data else ByteBuffer.allocateDirect(data.remaining()).put(data.duplicate()).flip()
    }

    override fun upload(level: Int, layer: Int, data: ByteBuffer?) {
        require(level in 0 until desc.mipLevels && layer in 0 until desc.layers) { "Level $level / layer $layer outside $desc" }
        if (data == null) return // contents undefined: nothing to copy
        val w = maxOf(1, desc.width shr level)
        val h = maxOf(1, desc.height shr level)
        val bytes = imageBytes(data)
        val expected = w * h * VkTranslate.imageTexelSize(desc.format)
        require(bytes.remaining() >= expected) { "Upload of level $level needs $expected bytes, got ${bytes.remaining()}" }
        val staging = factory.staging(bytes)
        try {
            core.oneShot { cmd ->
                transition(cmd, sampledLayout, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                stackPush().use { s ->
                    val region = VkBufferImageCopy.calloc(1, s)
                    region[0].bufferOffset(0)
                        .imageSubresource { it.aspectMask(aspect).mipLevel(level).baseArrayLayer(layer).layerCount(1) }
                        .imageExtent { it.width(w).height(h).depth(1) }
                    vkCmdCopyBufferToImage(cmd, staging.buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region)
                }
                transition(cmd, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, sampledLayout)
            }
        } finally {
            staging.close()
        }
    }

    /** Builds levels 1.. from level 0 of every layer (2D arrays and cube faces included) with blits. */
    override fun generateMips() {
        if (desc.mipLevels <= 1) return
        require(!isDepth) { "No mip generation for depth textures" }
        val filter = factory.blitFilter(format)
        core.oneShot { cmd ->
            transition(cmd, sampledLayout, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
            stackPush().use { s ->
                for (level in 1 until desc.mipLevels) {
                    transition(cmd, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, level - 1, 1)
                    val blit = VkImageBlit.calloc(1, s)
                    blit[0].srcSubresource { it.aspectMask(aspect).mipLevel(level - 1).baseArrayLayer(0).layerCount(desc.layers) }
                    blit[0].srcOffsets(1).set(maxOf(1, desc.width shr (level - 1)), maxOf(1, desc.height shr (level - 1)), 1)
                    blit[0].dstSubresource { it.aspectMask(aspect).mipLevel(level).baseArrayLayer(0).layerCount(desc.layers) }
                    blit[0].dstOffsets(1).set(maxOf(1, desc.width shr level), maxOf(1, desc.height shr level), 1)
                    vkCmdBlitImage(cmd, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, blit, filter)
                }
                // Every level but the last is TRANSFER_SRC now
                transition(cmd, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, sampledLayout, 0, desc.mipLevels - 1)
                transition(cmd, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, sampledLayout, desc.mipLevels - 1, 1)
            }
        }
    }

    /** Reads [level] of [layer] back as the image stores it, rows top-down (tests and diagnostics). */
    fun read(level: Int, layer: Int): ByteArray {
        val w = maxOf(1, desc.width shr level)
        val h = maxOf(1, desc.height shr level)
        val n = w * h * VkTranslate.imageTexelSize(desc.format)
        val staging = factory.allocator.buffer(n.toLong(), VK_BUFFER_USAGE_TRANSFER_DST_BIT, MemoryKind.HOST)
        try {
            core.oneShot { cmd ->
                transition(cmd, sampledLayout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                stackPush().use { s ->
                    val region = VkBufferImageCopy.calloc(1, s)
                    region[0].imageSubresource { it.aspectMask(aspect).mipLevel(level).baseArrayLayer(layer).layerCount(1) }
                        .imageExtent { it.set(w, h, 1) }
                    vkCmdCopyImageToBuffer(cmd, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, staging.buffer, region)
                }
                transition(cmd, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, sampledLayout)
            }
            return ByteArray(n).also { MemoryUtil.memByteBuffer(staging.address, n).get(it) }
        } finally {
            staging.close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        val views = layerViews.values.toList() + view
        layerViews.clear()
        core.destroyLaterOrNow {
            views.forEach { vkDestroyImageView(core.device, it, null) }
            allocation.close()
        }
    }

    companion object {
        fun viewType(type: TextureType) = when (type) {
            TextureType.TEXTURE_2D -> VK_IMAGE_VIEW_TYPE_2D
            TextureType.TEXTURE_2D_ARRAY -> VK_IMAGE_VIEW_TYPE_2D_ARRAY
            TextureType.CUBE -> VK_IMAGE_VIEW_TYPE_CUBE
        }
    }
}

/** Colour attachments plus optional depth; framebuffers are created per render pass and layer on first use. */
internal class VkRhiRenderTarget(private val factory: VkResourceFactory, desc: RenderTargetDesc) : RhiRenderTarget {
    private val core = factory.core
    override var desc: RenderTargetDesc = desc
        private set
    override val width: Int get() = desc.width
    override val height: Int get() = desc.height

    var colors: List<VkRhiTexture> = emptyList()
        private set
    var depth: VkRhiTexture? = null
        private set
    private val framebuffers = HashMap<Pair<Long, Int>, Long>()

    init {
        allocate()
    }

    private fun allocate() {
        colors = desc.colors.indices.map { VkRhiTexture(factory, desc.colorTexture(it), attachment = true) }
        depth = desc.depthTexture()?.let { VkRhiTexture(factory, it, attachment = true) }
    }

    override fun colorTexture(index: Int): RhiTexture = colors[index]
    override fun depthTexture(): RhiTexture? = depth

    /** Framebuffer of [renderPass] for [layer]. */
    fun framebuffer(renderPass: Long, layer: Int): Long = framebuffers.getOrPut(renderPass to layer) {
        stackPush().use { s ->
            val views = colors.map { it.layerView(layer) } + listOfNotNull(depth?.layerView(layer))
            val info = org.lwjgl.vulkan.VkFramebufferCreateInfo.calloc(s).`sType$Default`()
                .renderPass(renderPass).pAttachments(s.longs(*views.toLongArray()))
                .width(width).height(height).layers(1)
            val p = s.mallocLong(1)
            vk(vkCreateFramebuffer(core.device, info, null, p), "vkCreateFramebuffer")
            p[0]
        }
    }

    private fun release() {
        val fbs = framebuffers.values.toList()
        framebuffers.clear()
        core.destroyLaterOrNow { fbs.forEach { vkDestroyFramebuffer(core.device, it, null) } }
        colors.forEach { it.close() }
        depth?.close()
    }

    override fun resize(width: Int, height: Int) {
        if (width == desc.width && height == desc.height) return
        release()
        desc = desc.copy(width = width, height = height)
        allocate()
    }

    override fun close() = release()
}
