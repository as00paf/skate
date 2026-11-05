package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.graph.GpuQueryApi
import com.pafoid.skate.engine.render.rhi.BufferDesc
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RenderTargetDesc
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.RhiShader
import com.pafoid.skate.engine.render.rhi.PipelineDesc
import com.pafoid.skate.engine.render.rhi.RhiPipeline
import com.pafoid.skate.engine.render.rhi.ShaderDesc
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiCaps
import com.pafoid.skate.engine.render.rhi.RhiCommandList
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.RhiVertexLayout
import com.pafoid.skate.engine.render.rhi.VertexLayoutDesc
import org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_MAX_TEXTURE_SIZE
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_RENDERER
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_VERSION
import org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT
import org.lwjgl.opengl.GL11.glPixelStorei
import org.lwjgl.opengl.GL11.glReadBuffer
import org.lwjgl.opengl.GL11.glReadPixels
import org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGetFloat
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL11.glGetString
import org.lwjgl.opengl.GL13.GL_MULTISAMPLE
import org.lwjgl.opengl.GL30.GL_MAX_ARRAY_TEXTURE_LAYERS
import org.lwjgl.opengl.GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.opengl.GLUtil
import java.io.PrintStream
import java.nio.ByteBuffer

/**
 * The OpenGL 3.3 core backend of the RHI (REN-12). Owns the context's state cache ([GlStateCache]), binding cache
 * ([GLBindings]), current program ([GlPrograms]) and timer queries ([GlTimerQueries]).
 *
 * Create it with [create] once the window's context is current (it replaces `GL.createCapabilities()`); it then
 * becomes [RhiContext.device].
 */
class OpenGlDevice private constructor(
    val capabilities: GLCapabilities,
    override val caps: RhiCaps,
) : RhiDevice {

    /** GPU timer queries, or null when the context has none. */
    fun timerQueries(): GpuQueryApi? = GlTimerQueries.create()

    var isClosed = false
        private set

    /** Forgets the cached state, bindings and program: ImGui and the window system change them between frames. */
    override fun beginFrame() {
        GlState.cache.invalidate()
        GLBindings.cache.invalidate()
        GlPrograms.invalidate()
    }

    override fun endFrame() {
        // Nothing is deferred yet: closing a resource deletes it at once (deferred deletion arrives with step 4).
    }

    override fun createBuffer(desc: BufferDesc): RhiBuffer = GlBuffer.create(desc.usage, desc.update, desc.size)

    override fun createBuffer(usage: BufferUsage, update: BufferUpdate, data: FloatArray): RhiBuffer =
        GlBuffer.create(usage, update, data)

    override fun createBuffer(usage: BufferUsage, update: BufferUpdate, data: IntArray): RhiBuffer =
        GlBuffer.create(usage, update, data)

    override fun createVertexLayout(desc: VertexLayoutDesc): RhiVertexLayout = GlVertexLayout(desc)

    override fun createVertexInput(layout: RhiVertexLayout, vertexBuffers: List<RhiBuffer>, indexBuffer: RhiBuffer?): RhiVertexInput =
        GlVertexInput(layout as GlVertexLayout, vertexBuffers, indexBuffer)

    override fun createTexture(desc: TextureDesc): RhiTexture = GlTexture(desc, caps.maxAnisotropy > 1f)

    override fun createShader(desc: ShaderDesc): RhiShader = GlShader(desc)

    override fun createPipeline(desc: PipelineDesc): RhiPipeline = GlPipeline(desc)

    override fun createRenderTarget(desc: RenderTargetDesc): RhiRenderTarget = GlRenderTarget(desc, caps.maxAnisotropy > 1f)

    override fun readPixels(target: RhiRenderTarget?, attachment: Int, rect: IntRect, format: PixelFormat, out: ByteBuffer) {
        require(!format.isDepth) { "readPixels reads color attachments" }
        require(out.remaining() >= rect.width * rect.height * format.bytesPerPixel) { "Readback of $rect as $format overflows the buffer" }
        val fbo = (target as GlRenderTarget?)?.fbo ?: 0
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo)
        if (fbo != 0) glReadBuffer(GL_COLOR_ATTACHMENT0 + attachment)
        glPixelStorei(GL_PACK_ALIGNMENT, 1)
        val (_, glFormat, glType) = GlFormats.texture(format)
        glReadPixels(rect.x, rect.y, rect.width, rect.height, glFormat, glType, out)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0)
    }

    override fun commandList(): RhiCommandList = GlCommandList

    /** The context itself belongs to the window; this device stops being the current one. */
    override fun close() {
        isClosed = true
        if (RhiContext.device === this) RhiContext.device = null
    }

    companion object {
        const val BACKEND = "OpenGL 3.3 core"

        /**
         * Loads the GL functions of the current context and sets the engine's initial context state: alpha blending,
         * seamless cube maps and multisampling (what `Window` used to do).
         *
         * @param debugOutput Receives KHR_debug messages when set (debug contexts only)
         */
        fun create(debugOutput: PrintStream? = null): OpenGlDevice {
            val capabilities = GL.createCapabilities()
            debugOutput?.let { GLUtil.setupDebugMessageCallback(it) }

            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)
            glEnable(GL_MULTISAMPLE)

            val anisotropic = capabilities.GL_EXT_texture_filter_anisotropic || capabilities.GL_ARB_texture_filter_anisotropic
            val caps = RhiCaps(
                backend = BACKEND,
                renderer = glGetString(GL_RENDERER).orEmpty(),
                version = glGetString(GL_VERSION).orEmpty(),
                maxTextureSize = glGetInteger(GL_MAX_TEXTURE_SIZE),
                maxArrayLayers = glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS),
                maxAnisotropy = if (anisotropic) glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT) else 1f,
                timerQueries = capabilities.OpenGL33 || capabilities.GL_ARB_timer_query,
            )
            return OpenGlDevice(capabilities, caps).also { RhiContext.device = it }
        }
    }
}
