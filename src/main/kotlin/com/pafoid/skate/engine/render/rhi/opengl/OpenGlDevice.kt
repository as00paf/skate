package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.graph.GpuQueryApi
import com.pafoid.skate.engine.render.rhi.BufferDesc
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiCaps
import com.pafoid.skate.engine.render.rhi.RhiCommandList
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
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

/**
 * The OpenGL 3.3 core backend of the RHI (REN-12). Owns the context's state cache ([GLStateTracker]), binding cache
 * ([GLBindings]) and timer queries ([GlTimerQueries]); renderers still reach those through the existing objects
 * until steps 2-7 move them behind [RhiDevice].
 *
 * Create it with [create] once the window's context is current (it replaces `GL.createCapabilities()`); it then
 * becomes [RhiContext.device].
 */
class OpenGlDevice private constructor(
    val capabilities: GLCapabilities,
    override val caps: RhiCaps,
) : RhiDevice {

    /** Fixed-function state cache of this context. */
    val stateTracker: GLStateTracker get() = GLStateTracker

    /** VAO/texture binding cache of this context. */
    val bindings: BindingCache get() = GLBindings.cache

    /** GPU timer queries, or null when the context has none. */
    fun timerQueries(): GpuQueryApi? = GlTimerQueries.create()

    var isClosed = false
        private set

    override fun beginFrame() {
        GLStateTracker.invalidateCache()
        GLBindings.cache.invalidate()
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
