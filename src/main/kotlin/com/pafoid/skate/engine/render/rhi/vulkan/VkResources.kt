package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RenderTargetDesc
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.RhiVertexLayout
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.VertexLayoutDesc
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferImageCopy
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkImageBlit
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkImageMemoryBarrier
import org.lwjgl.vulkan.VkImageViewCreateInfo
import org.lwjgl.vulkan.VkSamplerCreateInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * An RHI buffer on Vulkan. Vertex, index and instance buffers live in mapped host-visible memory (writes land at once;
 * the spike waits for the GPU after every pass, so there is no hazard between frames). Uniform buffers are only a CPU
 * copy: each draw snapshots them into the uniform ring, which gives GL's "value at draw time" semantics.
 */
internal class VkRhiBuffer(
    private val core: VkCore,
    override val usage: BufferUsage,
    override val update: BufferUpdate,
    size: Long,
) : RhiBuffer {
    override var size: Long = size
        private set

    /** CPU contents of a uniform buffer. */
    var contents: ByteBuffer = ByteBuffer.allocate(if (usage == BufferUsage.UNIFORM) size.toInt().coerceAtLeast(16) else 0).order(ByteOrder.LITTLE_ENDIAN)
        private set

    var gpu: HostBuffer? = if (usage == BufferUsage.UNIFORM) null else core.hostBuffer(size, usageFlags(usage))
        private set

    private fun usageFlags(usage: BufferUsage): Int = when (usage) {
        BufferUsage.VERTEX, BufferUsage.INSTANCE -> VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
        BufferUsage.INDEX -> VK_BUFFER_USAGE_INDEX_BUFFER_BIT
        BufferUsage.UNIFORM -> VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
    }

    private fun write(offset: Long, src: ByteBuffer) {
        val n = src.remaining()
        require(offset + n <= size) { "Write of $n bytes at $offset overflows a $size-byte buffer" }
        val g = gpu
        if (g != null) MemoryUtil.memCopy(MemoryUtil.memAddress(src), g.address + offset, n.toLong())
        else contents.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(offset.toInt()).let { (it as ByteBuffer).put(src.duplicate()) }
    }

    private fun direct(n: Int, fill: (ByteBuffer) -> Unit): ByteBuffer =
        MemoryUtil.memAlloc(n).order(ByteOrder.nativeOrder()).also(fill).also { it.flip() }

    override fun update(offset: Long, data: FloatArray) {
        val b = direct(data.size * 4) { it.asFloatBuffer().put(data); it.position(data.size * 4) }
        try { write(offset, b) } finally { MemoryUtil.memFree(b) }
    }

    override fun update(offset: Long, data: FloatBuffer) {
        val n = data.remaining() * 4
        if (data.isDirect) {
            write(offset, MemoryUtil.memByteBuffer(MemoryUtil.memAddress(data), n))
        } else {
            val b = direct(n) { it.asFloatBuffer().put(data.duplicate()); it.position(n) }
            try { write(offset, b) } finally { MemoryUtil.memFree(b) }
        }
    }

    override fun update(offset: Long, data: ByteBuffer) {
        if (data.isDirect) write(offset, data)
        else {
            val b = direct(data.remaining()) { it.put(data.duplicate()) }
            try { write(offset, b) } finally { MemoryUtil.memFree(b) }
        }
    }

    override fun resize(size: Long) {
        this.size = size
        if (usage == BufferUsage.UNIFORM) {
            contents = ByteBuffer.allocate(size.toInt().coerceAtLeast(16)).order(ByteOrder.LITTLE_ENDIAN)
        } else {
            gpu?.close()
            gpu = core.hostBuffer(size, usageFlags(usage))
        }
    }

    override fun replace(data: FloatArray) {
        resize(maxOf(data.size * 4L, 4L))
        if (data.isNotEmpty()) update(0, data)
    }

    override fun replace(data: IntArray) {
        resize(maxOf(data.size * 4L, 4L))
        if (data.isEmpty()) return
        val b = direct(data.size * 4) { it.asIntBuffer().put(data); it.position(data.size * 4) }
        try { write(0, b) } finally { MemoryUtil.memFree(b) }
    }

    override fun close() {
        gpu?.close()
        gpu = null
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
 * A sampled image: 2D, 2D array or cube, colour or depth. Rests in the layout shaders read it in
 * ([sampledLayout]); uploads, mip generation and render passes transition away and back.
 */
internal class VkRhiTexture(
    private val core: VkCore,
    override val desc: TextureDesc,
    attachment: Boolean = false,
) : RhiTexture {
    val format = VkTranslate.imageFormat(desc.format)
    val isDepth = desc.format.isDepth
    val aspect = if (isDepth) VK_IMAGE_ASPECT_DEPTH_BIT else VK_IMAGE_ASPECT_COLOR_BIT
    val sampledLayout = if (isDepth) VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL else VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
    val image: Long
    private val memory: Long
    val view: Long
    var vkSampler: Long = VK_NULL_HANDLE
        private set
    private val layerViews = HashMap<Int, Long>()
    private var closed = false

    override var sampler: SamplerDesc = desc.sampler
        private set

    override val nativeHandle: Long get() = image

    init {
        image = stackPush().use { s ->
            var usage = VK_IMAGE_USAGE_SAMPLED_BIT or VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_TRANSFER_SRC_BIT
            if (attachment) usage = usage or if (isDepth) VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT else VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
            val info = VkImageCreateInfo.calloc(s).`sType$Default`()
                .imageType(VK_IMAGE_TYPE_2D).format(format)
                .extent { it.width(desc.width).height(desc.height).depth(1) }
                .mipLevels(desc.mipLevels).arrayLayers(desc.layers)
                .samples(VK_SAMPLE_COUNT_1_BIT).tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            if (desc.type == TextureType.CUBE) info.flags(VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT)
            val p = s.mallocLong(1)
            vk(vkCreateImage(core.device, info, null, p), "vkCreateImage")
            p[0]
        }
        memory = core.imageMemory(image, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
        view = createView(
            when (desc.type) {
                TextureType.TEXTURE_2D -> VK_IMAGE_VIEW_TYPE_2D
                TextureType.TEXTURE_2D_ARRAY -> VK_IMAGE_VIEW_TYPE_2D_ARRAY
                TextureType.CUBE -> VK_IMAGE_VIEW_TYPE_CUBE
            },
            0, desc.layers, desc.mipLevels,
        )
        applySampler(desc.sampler)
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

    /** Single-layer 2D view for rendering into [layer] of a layered target. */
    fun layerView(layer: Int): Long = if (desc.layers == 1 && desc.type == TextureType.TEXTURE_2D) view
        else layerViews.getOrPut(layer) { createView(VK_IMAGE_VIEW_TYPE_2D, layer, 1, 1) }

    private fun applySampler(s: SamplerDesc) {
        if (vkSampler != VK_NULL_HANDLE) vkDestroySampler(core.device, vkSampler, null)
        vkSampler = stackPush().use { st ->
            val aniso = core.anisotropy && s.anisotropy > 1f
            val info = VkSamplerCreateInfo.calloc(st).`sType$Default`()
                .magFilter(VkTranslate.filter(s.magFilter)).minFilter(VkTranslate.filter(s.minFilter))
                .mipmapMode(VkTranslate.mipmapMode(s.minFilter))
                .addressModeU(VkTranslate.addressMode(s.wrap)).addressModeV(VkTranslate.addressMode(s.wrapV))
                .addressModeW(VkTranslate.addressMode(s.wrap))
                .anisotropyEnable(aniso).maxAnisotropy(if (aniso) minOf(s.anisotropy, core.maxAnisotropy) else 1f)
                .compareEnable(s.compare != null).compareOp(VkTranslate.compareOp(s.compare ?: CompareFunc.ALWAYS))
                .minLod(0f).maxLod(if (VkTranslate.usesMips(s.minFilter)) desc.mipLevels.toFloat() else 0.25f)
                .borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK)
            val p = st.mallocLong(1)
            vk(vkCreateSampler(core.device, info, null, p), "vkCreateSampler")
            p[0]
        }
        sampler = s
    }

    override fun setSampler(sampler: SamplerDesc) {
        if (sampler != this.sampler) applySampler(sampler)
    }

    fun transition(cmd: VkCommandBuffer, from: Int, to: Int, baseLevel: Int = 0, levels: Int = desc.mipLevels) = stackPush().use { s ->
        val barrier = VkImageMemoryBarrier.calloc(1, s)
        barrier[0].`sType$Default`().oldLayout(from).newLayout(to)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).image(image)
            .subresourceRange { it.aspectMask(aspect).baseMipLevel(baseLevel).levelCount(levels).baseArrayLayer(0).layerCount(desc.layers) }
            .srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT).dstAccessMask(VK_ACCESS_MEMORY_READ_BIT or VK_ACCESS_MEMORY_WRITE_BIT)
        vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barrier)
    }

    override fun upload(level: Int, layer: Int, data: ByteBuffer?) {
        require(level in 0 until desc.mipLevels && layer in 0 until desc.layers)
        if (data == null) return // contents undefined: nothing to copy
        val w = maxOf(1, desc.width shr level)
        val h = maxOf(1, desc.height shr level)
        val bytes = ByteArray(data.remaining()).also { data.duplicate().get(it) }
        val texels = when (desc.format) {
            PixelFormat.RGB8 -> VkTranslate.widenToFour(bytes, 1, byteArrayOf(-1))
            PixelFormat.RGB32F -> VkTranslate.widenToFour(bytes, 4, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(1f).array())
            else -> bytes
        }
        val staging = core.hostBuffer(texels.size.toLong(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
        try {
            MemoryUtil.memByteBuffer(staging.address, texels.size).put(texels)
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

    override fun generateMips() {
        if (desc.mipLevels <= 1) return
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
                    vkCmdBlitImage(cmd, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, blit, VK_FILTER_LINEAR)
                }
                // Every level but the last is TRANSFER_SRC now
                transition(cmd, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, sampledLayout, 0, desc.mipLevels - 1)
                transition(cmd, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, sampledLayout, desc.mipLevels - 1, 1)
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        layerViews.values.forEach { vkDestroyImageView(core.device, it, null) }
        vkDestroySampler(core.device, vkSampler, null)
        vkDestroyImageView(core.device, view, null)
        vkDestroyImage(core.device, image, null)
        vkFreeMemory(core.device, memory, null)
    }
}

/** Colour attachments plus optional depth; framebuffers are created per render pass and layer on first use. */
internal class VkRhiRenderTarget(private val core: VkCore, desc: RenderTargetDesc) : RhiRenderTarget {
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
        colors = desc.colors.indices.map { VkRhiTexture(core, desc.colorTexture(it), attachment = true) }
        depth = desc.depthTexture()?.let { VkRhiTexture(core, it, attachment = true) }
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
        framebuffers.values.forEach { vkDestroyFramebuffer(core.device, it, null) }
        framebuffers.clear()
        colors.forEach { it.close() }
        depth?.close()
    }

    override fun resize(width: Int, height: Int) {
        if (width == desc.width && height == desc.height) return
        vkDeviceWaitIdle(core.device)
        release()
        desc = desc.copy(width = width, height = height)
        allocate()
    }

    override fun close() = release()
}
