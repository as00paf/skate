package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.graph.GpuQueryApi
import com.pafoid.skate.engine.render.rhi.RhiCaps
import com.pafoid.skate.engine.render.rhi.RhiDevice
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
 * Create it with [create] once the window's context is current (it replaces `GL.createCapabilities()`).
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
        // Nothing is deferred yet: resources still delete immediately (GpuResource) until step 4.
    }

    /** The context itself belongs to the window; nothing backend-owned needs releasing yet. */
    override fun close() {
        isClosed = true
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
            return OpenGlDevice(capabilities, caps)
        }
    }
}
