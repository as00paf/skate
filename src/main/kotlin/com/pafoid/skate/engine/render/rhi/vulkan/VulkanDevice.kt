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
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferImageCopy
import java.nio.ByteBuffer

/**
 * Offscreen Vulkan backend of the RHI (REN-13 spike, docs/design/REN-13.md). Opt-in only ([RhiBackend]): the editor
 * and the game keep OpenGL. Implements what the geometry pass needs: buffers, textures with mips, render targets,
 * the engine's shaders rewritten for Vulkan ([GlslToVulkan]), pipelines, draws and readback. No swapchain, blit,
 * timers or ImGui; every pass is submitted and waited for.
 */
class VulkanDevice private constructor(private val core: VkCore) : RhiDevice {

    override val caps = RhiCaps(
        backend = BACKEND,
        renderer = core.deviceName,
        version = "Vulkan ${core.apiVersion ushr 22}.${(core.apiVersion ushr 12) and 0x3FF}.${core.apiVersion and 0xFFF}",
        maxTextureSize = core.maxImageDimension2D,
        maxArrayLayers = core.maxImageArrayLayers,
        maxAnisotropy = if (core.anisotropy) core.maxAnisotropy else 1f,
        timerQueries = false,
    )

    private val commands = VkCommandList(core, this)
    private val renderPasses = HashMap<List<Any>, Long>()
    private val pipelines = ArrayList<VkRhiPipeline>()

    /** Memory, uploads, samplers, descriptor layouts and per-frame transient resources (VK-02). */
    internal val resources = VkResourceFactory(core)

    override fun beginFrame() {}
    override fun endFrame() {}

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

    internal fun buildPipeline(p: VkRhiPipeline, compiled: VkRhiShader.Compiled, input: VkRhiVertexInput, renderPass: Long, colors: Int) =
        VkPipelineBuilder.build(core, p, compiled, input, renderPass, colors)

    /**
     * Copies [rect] of colour attachment [attachment] in GL order (bottom row first), undoing the backend's Y flip, so
     * readback matches the OpenGL backend byte for byte.
     */
    override fun readPixels(target: RhiRenderTarget?, attachment: Int, rect: IntRect, format: PixelFormat, out: ByteBuffer) {
        val t = requireNotNull(target as VkRhiRenderTarget?) { "The Vulkan spike has no back buffer to read" }
        val tex = t.colors[attachment]
        val texel = VkTranslate.imageTexelSize(tex.desc.format)
        val rowBytes = rect.width * texel
        val staging = core.hostBuffer(rowBytes.toLong() * rect.height, VK_BUFFER_USAGE_TRANSFER_DST_BIT)
        try {
            core.oneShot { cmd ->
                tex.transition(cmd, tex.sampledLayout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                stackPush().use { s ->
                    val region = VkBufferImageCopy.calloc(1, s)
                    region[0].imageSubresource { it.aspectMask(tex.aspect).mipLevel(0).baseArrayLayer(0).layerCount(1) }
                        .imageOffset { it.set(rect.x, t.height - (rect.y + rect.height), 0) }
                        .imageExtent { it.set(rect.width, rect.height, 1) }
                    vkCmdCopyImageToBuffer(cmd, tex.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, staging.buffer, region)
                }
                tex.transition(cmd, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, tex.sampledLayout)
            }
            val src = MemoryUtil.memByteBuffer(staging.address, rowBytes * rect.height)
            val outTexel = format.bytesPerPixel
            val base = out.position()
            for (row in 0 until rect.height) {
                val srcRow = rect.height - 1 - row // Vulkan memory is top row first
                for (x in 0 until rect.width) for (b in 0 until outTexel) {
                    out.put(base + (row * rect.width + x) * outTexel + b, src.get(srcRow * rowBytes + x * texel + b))
                }
            }
        } finally {
            staging.close()
        }
    }

    override fun commandList(): RhiCommandList = commands

    override fun close() {
        vkDeviceWaitIdle(core.device)
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
        const val BACKEND = "Vulkan 1.1 (offscreen spike)"

        /** A device on the first Vulkan GPU; it becomes [RhiContext.device]. Throws when Vulkan is unavailable. */
        fun create(): VulkanDevice = VulkanDevice(VkCore.create()).also { RhiContext.device = it }
    }
}

/**
 * Which RHI backend a run asked for: `-Dskate.rhi=vulkan` or `SKATE_RHI=vulkan` selects the offscreen Vulkan spike,
 * anything else (the default) OpenGL. Only the Vulkan frame tool and tests honour it; windows always use OpenGL.
 */
object RhiBackend {
    const val OPENGL = "opengl"
    const val VULKAN = "vulkan"

    fun selected(property: String? = System.getProperty("skate.rhi"), env: String? = System.getenv("SKATE_RHI")): String =
        (property ?: env)?.trim()?.lowercase()?.takeIf { it == VULKAN } ?: OPENGL
}
