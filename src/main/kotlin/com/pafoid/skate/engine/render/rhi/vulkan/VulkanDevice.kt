package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.render.rhi.BufferDesc
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PipelineDesc
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RenderTargetDesc
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiCaps
import com.pafoid.skate.engine.render.rhi.RhiCommandList
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiPipeline
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.RhiShader
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.RhiTimer
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.RhiVertexLayout
import com.pafoid.skate.engine.render.rhi.ShaderDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.VertexLayoutDesc
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferImageCopy
import java.nio.ByteBuffer

/**
 * Vulkan backend of the RHI (REN-13 spike, VK-01; docs/design/REN-13.md). Opt-in only ([RhiBackend]): the editor
 * always uses OpenGL. Implements buffers, textures with mips, render targets, the engine's shaders rewritten for
 * Vulkan ([GlslToVulkan]), pipelines, draws, blits and readback.
 *
 * With a window (a GLFW window created with `GLFW_CLIENT_API = GLFW_NO_API`) it presents: the back buffer is the
 * RHI default target (`beginPass(null)`, `blit(src, null)`), recreated on resize, skipped while minimized. Frames
 * are recorded into one command buffer per frame in flight ([VkFrameLoop]); [endFrame] submits and presents, and
 * [beginFrame] ends a frame left open. Without a window it renders offscreen only. No GPU timers or ImGui yet.
 */
class VulkanDevice private constructor(
    private val core: VkCore,
    window: Long,
    vsync: Boolean,
    /** Frames the CPU may record ahead of the GPU. */
    val framesInFlight: Int,
) : RhiDevice {

    override val caps = RhiCaps(
        backend = BACKEND,
        renderer = core.deviceName,
        version = "Vulkan ${core.apiVersion ushr 22}.${(core.apiVersion ushr 12) and 0x3FF}.${core.apiVersion and 0xFFF}",
        maxTextureSize = core.maxImageDimension2D,
        maxArrayLayers = core.maxImageArrayLayers,
        maxAnisotropy = if (core.anisotropy) core.maxAnisotropy else 1f,
        timerQueries = false,
    )

    /** Memory, uploads, samplers, descriptor layouts and per-frame transient resources (VK-02). */
    internal val resources = VkResourceFactory(core, framesInFlight)

    private val commands = VkCommandList(core, this, framesInFlight)
    private val frames = VkFrameLoop(
        core,
        if (window != NULL) VkSwapchain(core, window, vsync, framesInFlight) else null,
        framesInFlight,
        ::onFrameSlotReady,
    ).also { commands.attach(it) }

    /** Whether frames wait for the display's vertical blank (FIFO) or not (MAILBOX, else IMMEDIATE). */
    var vsync: Boolean
        get() = frames.swapchain?.vsync ?: false
        set(value) { frames.swapchain?.vsync = value }

    /** True when this device presents to a window. */
    val presents: Boolean get() = frames.swapchain != null

    /** Back buffer size (0x0 before the first frame, offscreen, or while minimized). */
    val backbufferWidth: Int get() = frames.swapchain?.width ?: 0
    val backbufferHeight: Int get() = frames.swapchain?.height ?: 0

    /** Times the swapchain was (re)built; grows on resize and after a minimize. */
    val swapchainGeneration: Int get() = frames.swapchain?.generation ?: 0

    /** Frames submitted so far. */
    val frameCount: Long get() = frames.ring.frameCount

    /** Whether the Khronos validation layer is active on this device. */
    val validation: Boolean get() = core.validation

    /** The GPU is done with frame slot [slot]: its uniform/vertex rings and descriptor pools are reused (VK-02). */
    private fun onFrameSlotReady(slot: Int) {
        resources.frames.beginFrame(slot)
    }
    private val renderPasses = HashMap<List<Any>, Long>()
    private val pipelines = ArrayList<VkRhiPipeline>()

    /** Starts a frame, ending (submitting and presenting) one still open; waits when [framesInFlight] are queued. */
    override fun beginFrame() = frames.beginFrame()

    /** Submits the frame and presents its back buffer, if it drew to one. */
    override fun endFrame() = frames.endFrame()

    override fun createBuffer(desc: BufferDesc): RhiBuffer = resources.createBuffer(desc)

    override fun createBuffer(usage: BufferUsage, update: BufferUpdate, data: FloatArray): RhiBuffer =
        resources.createBuffer(usage, update, data)

    override fun createBuffer(usage: BufferUsage, update: BufferUpdate, data: IntArray): RhiBuffer =
        resources.createBuffer(usage, update, data)

    override fun createVertexLayout(desc: VertexLayoutDesc): RhiVertexLayout = resources.createVertexLayout(desc)

    override fun createVertexInput(layout: RhiVertexLayout, vertexBuffers: List<RhiBuffer>, indexBuffer: RhiBuffer?): RhiVertexInput =
        resources.createVertexInput(layout, vertexBuffers, indexBuffer)

    override fun createTexture(desc: TextureDesc): RhiTexture = resources.createTexture(desc)

    override fun createShader(desc: ShaderDesc): RhiShader = resources.createShader(desc)

    override fun createTimer(): RhiTimer = NoVkTimer

    override fun createPipeline(desc: PipelineDesc): RhiPipeline = VkRhiPipeline(desc).also { p ->
        p.destroy = { vkDestroyPipeline(core.device, it.handle, null) }
        pipelines += p
    }

    override fun createRenderTarget(desc: RenderTargetDesc): RhiRenderTarget = resources.createRenderTarget(desc)

    internal fun renderPass(target: VkRhiRenderTarget, clearColor: Boolean, clearDepth: Boolean): Long {
        val colors = target.colors.map { it.format }.toIntArray()
        val depth = target.depth?.format
        return renderPasses.getOrPut(listOf(colors.toList(), depth ?: -1, clearColor, clearDepth)) {
            VkPipelineBuilder.renderPass(core, colors, depth, clearColor, clearDepth)
        }
    }

    internal fun backbufferRenderPass(format: Int, clear: Boolean, initialLayout: Int): Long =
        renderPasses.getOrPut(listOf("backbuffer", format, clear, if (clear) VK_IMAGE_LAYOUT_UNDEFINED else initialLayout)) {
            VkPipelineBuilder.backbufferPass(core, format, clear, initialLayout)
        }

    internal fun buildPipeline(p: VkRhiPipeline, compiled: VkRhiShader.Compiled, input: VkRhiVertexInput, renderPass: Long, colors: Int) =
        VkPipelineBuilder.build(core, p, compiled, input, renderPass, colors)

    /**
     * Copies [rect] of colour attachment [attachment] (or of this frame's back buffer when [target] is null) in GL
     * order (bottom row first), undoing the backend's Y flip, so readback matches the OpenGL backend byte for byte.
     * Commands recorded so far in the frame are submitted and waited for first.
     */
    override fun readPixels(target: RhiRenderTarget?, attachment: Int, rect: IntRect, format: PixelFormat, out: ByteBuffer) {
        frames.flushAndWait()
        val t = target as VkRhiRenderTarget?
        val image: Long
        val aspect: Int
        val texel: Int
        val height: Int
        val restLayout: Int
        var swapRedBlue = false
        if (t != null) {
            val tex = t.colors[attachment]
            image = tex.image
            aspect = tex.aspect
            texel = VkTranslate.imageTexelSize(tex.desc.format)
            height = t.height
            restLayout = tex.sampledLayout
        } else {
            val sc = checkNotNull(frames.swapchain) { "An offscreen Vulkan device has no back buffer to read" }
            check(frames.backbuffer >= 0 && frames.backbufferLayout == VkSwapchain.PRESENT_LAYOUT) {
                "Read the back buffer after drawing to it and before endFrame"
            }
            check(sc.readable) { "This surface's swapchain images cannot be read back" }
            image = sc.images[frames.backbuffer]
            aspect = VK_IMAGE_ASPECT_COLOR_BIT
            texel = 4
            height = sc.height
            restLayout = VkSwapchain.PRESENT_LAYOUT
            swapRedBlue = sc.format == VK_FORMAT_B8G8R8A8_UNORM || sc.format == VK_FORMAT_B8G8R8A8_SRGB
        }
        val rowBytes = rect.width * texel
        val staging = core.hostBuffer(rowBytes.toLong() * rect.height, VK_BUFFER_USAGE_TRANSFER_DST_BIT)
        try {
            core.oneShot { cmd ->
                stackPush().use { s ->
                    imageBarrier(cmd, image, aspect, restLayout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    val region = VkBufferImageCopy.calloc(1, s)
                    region[0].imageSubresource { it.aspectMask(aspect).mipLevel(0).baseArrayLayer(0).layerCount(1) }
                        .imageOffset { it.set(rect.x, height - (rect.y + rect.height), 0) }
                        .imageExtent { it.set(rect.width, rect.height, 1) }
                    vkCmdCopyImageToBuffer(cmd, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, staging.buffer, region)
                    imageBarrier(cmd, image, aspect, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, restLayout)
                }
            }
            val src = MemoryUtil.memByteBuffer(staging.address, rowBytes * rect.height)
            val outTexel = format.bytesPerPixel
            val base = out.position()
            for (row in 0 until rect.height) {
                val srcRow = rect.height - 1 - row // Vulkan memory is top row first
                for (x in 0 until rect.width) for (b in 0 until outTexel) {
                    val from = if (swapRedBlue && b < 3) 2 - b else b
                    out.put(base + (row * rect.width + x) * outTexel + b, src.get(srcRow * rowBytes + x * texel + from))
                }
            }
        } finally {
            staging.close()
        }
    }

    private fun imageBarrier(cmd: org.lwjgl.vulkan.VkCommandBuffer, image: Long, aspect: Int, from: Int, to: Int) = stackPush().use { s ->
        val barrier = org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, s)
        barrier[0].`sType$Default`().oldLayout(from).newLayout(to)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).image(image)
            .subresourceRange { it.aspectMask(aspect).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1) }
            .srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT).dstAccessMask(VK_ACCESS_MEMORY_READ_BIT or VK_ACCESS_MEMORY_WRITE_BIT)
        vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barrier)
    }

    override fun commandList(): RhiCommandList = commands

    override fun close() {
        frames.close()
        pipelines.forEach { it.close() }
        renderPasses.values.forEach { vkDestroyRenderPass(core.device, it, null) }
        commands.destroy()
        resources.close()
        if (RhiContext.device === this) RhiContext.device = null
        core.close()
    }

    private object NoVkTimer : RhiTimer {
        override val latestNs: Long = -1
        override fun close() {}
    }

    companion object {
        const val BACKEND = "Vulkan 1.1"

        /**
         * A device on the first Vulkan GPU; it becomes [RhiContext.device]. Throws when Vulkan is unavailable.
         * [window] (a GLFW no-API window, or 0 for offscreen) gets a surface and swapchain; [validation] enables the
         * Khronos layer when installed (default: [VkCore.validationRequested]).
         */
        fun create(
            window: Long = NULL,
            vsync: Boolean = true,
            framesInFlight: Int = FrameRing.DEFAULT_FRAMES_IN_FLIGHT,
            validation: Boolean = VkCore.validationRequested(),
        ): VulkanDevice {
            val core = VkCore.create(window, validation)
            return try {
                VulkanDevice(core, window, vsync, framesInFlight).also { RhiContext.device = it }
            } catch (e: Throwable) {
                runCatching { core.close() }
                throw e
            }
        }

        /** Validation errors reported so far in this JVM ([VkCore.validationErrors]); tests check it does not grow. */
        val validationErrors: Int get() = VkCore.validationErrors.get()

        /** Whether the Khronos validation layer can be enabled. */
        fun validationLayerInstalled(): Boolean = VkCore.validationLayerInstalled()
    }
}

/**
 * Which RHI backend a run asked for: `-Dskate.rhi=vulkan` or `SKATE_RHI=vulkan` selects Vulkan, anything else (the
 * default) OpenGL. Only the Vulkan tests honour it so far; the editor and game windows use OpenGL (GPL-19 wires the
 * exported game's choice).
 */
object RhiBackend {
    const val OPENGL = "opengl"
    const val VULKAN = "vulkan"

    fun selected(property: String? = System.getProperty("skate.rhi"), env: String? = System.getenv("SKATE_RHI")): String =
        (property ?: env)?.trim()?.lowercase()?.takeIf { it == VULKAN } ?: OPENGL
}
