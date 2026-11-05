package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.EngineStats
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glDepthFunc
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGetBoolean
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL11.glIsEnabled
import org.lwjgl.opengl.GL30.GL_BLEND
import org.lwjgl.opengl.GL30.GL_CULL_FACE
import org.lwjgl.opengl.GL30.GL_DEPTH_FUNC
import org.lwjgl.opengl.GL30.GL_DEPTH_TEST
import org.lwjgl.opengl.GL30.GL_DEPTH_WRITEMASK
import org.lwjgl.opengl.GL30.glDepthMask
import org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA
import org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB
import org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA
import org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB
import org.lwjgl.opengl.GL14.glBlendFuncSeparate

/**
 * Centralized OpenGL state tracker that minimizes redundant OpenGL calls.
 * Tracks current state and only applies changes when state actually differs.
 *
 * This is a singleton object that maintains global OpenGL state.
 * All state changes should go through this tracker for optimal performance.
 */
object GLStateTracker {

    // Cached state values
    private var blendEnabled: Boolean = false
    private var depthTestEnabled: Boolean = true
    private var depthMaskEnabled: Boolean = true
    private var cullFaceEnabled: Boolean = false
    private var depthFunc: Int = GL11.GL_LESS

    // Blend function cache (RGB and alpha factors); synced from GL by initialize/invalidateCache
    private val blendFactors = IntArray(4) { UNKNOWN }

    /**
     * Initializes the state tracker by querying current OpenGL state.
     * Should be called once during engine initialization.
     */
    fun initialize() {
        blendEnabled = glIsEnabled(GL_BLEND)
        depthTestEnabled = glIsEnabled(GL_DEPTH_TEST)
        depthMaskEnabled = glGetBoolean(GL_DEPTH_WRITEMASK)
        cullFaceEnabled = glIsEnabled(GL_CULL_FACE)
        depthFunc = glGetInteger(GL_DEPTH_FUNC)
        readBlendFactors()
    }

    /**
     * Enables or disables blending, only making the GL call if state changes.
     */
    fun setBlendEnabled(enabled: Boolean) {
        if (blendEnabled != enabled) {
            if (enabled) {
                glEnable(GL_BLEND)
            } else {
                glDisable(GL_BLEND)
            }
            blendEnabled = enabled
            EngineStats.stateChanges.incrementAndGet()
        }
    }

    /**
     * Returns whether blending is currently enabled.
     */
    fun isBlendEnabled(): Boolean = blendEnabled

    /**
     * Sets the blend function for RGB and alpha alike, only making the GL call if state changes.
     */
    fun setBlendFunc(sfactor: Int, dfactor: Int) = setBlendFuncSeparate(sfactor, dfactor, sfactor, dfactor)

    /**
     * Sets separate RGB and alpha blend factors, only making the GL call if state changes.
     * Overlays drawn over an opaque target use (SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA) so the
     * target alpha stays 1: the editor viewport composites the scene texture with its alpha.
     */
    fun setBlendFuncSeparate(srcRgb: Int, dstRgb: Int, srcAlpha: Int, dstAlpha: Int) {
        if (BlendFactors.matches(blendFactors, srcRgb, dstRgb, srcAlpha, dstAlpha)) return
        glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
        BlendFactors.store(blendFactors, srcRgb, dstRgb, srcAlpha, dstAlpha)
        EngineStats.stateChanges.incrementAndGet()
    }

    private fun readBlendFactors() = BlendFactors.store(
        blendFactors,
        glGetInteger(GL_BLEND_SRC_RGB), glGetInteger(GL_BLEND_DST_RGB),
        glGetInteger(GL_BLEND_SRC_ALPHA), glGetInteger(GL_BLEND_DST_ALPHA),
    )

    private const val UNKNOWN = -1

    /**
     * Enables or disables depth testing, only making the GL call if state changes.
     */
    fun setDepthTestEnabled(enabled: Boolean) {
        if (depthTestEnabled != enabled) {
            if (enabled) {
                glEnable(GL_DEPTH_TEST)
            } else {
                glDisable(GL_DEPTH_TEST)
            }
            depthTestEnabled = enabled
            EngineStats.stateChanges.incrementAndGet()
        }
    }

    /**
     * Returns whether depth testing is currently enabled.
     */
    fun isDepthTestEnabled(): Boolean = depthTestEnabled

    /**
     * Sets the depth mask (whether depth writes are enabled),
     * only making the GL call if state changes.
     */
    fun setDepthMask(enabled: Boolean) {
        if (depthMaskEnabled != enabled) {
            glDepthMask(enabled)
            depthMaskEnabled = enabled
            EngineStats.stateChanges.incrementAndGet()
        }
    }

    /**
     * Returns whether depth writes are currently enabled.
     */
    fun isDepthMaskEnabled(): Boolean = depthMaskEnabled

    /**
     * Sets the depth comparison function, only making the GL call if state changes.
     */
    fun setDepthFunc(func: Int) {
        if (depthFunc != func) {
            glDepthFunc(func)
            depthFunc = func
            EngineStats.stateChanges.incrementAndGet()
        }
    }

    /**
     * Returns the current depth comparison function.
     */
    fun getDepthFunc(): Int = depthFunc

    /**
     * Enables or disables face culling, only making the GL call if state changes.
     */
    fun setCullFaceEnabled(enabled: Boolean) {
        if (cullFaceEnabled != enabled) {
            if (enabled) {
                glEnable(GL_CULL_FACE)
            } else {
                glDisable(GL_CULL_FACE)
            }
            cullFaceEnabled = enabled
            EngineStats.stateChanges.incrementAndGet()
        }
    }

    /**
     * Returns whether face culling is currently enabled.
     */
    fun isCullFaceEnabled(): Boolean = cullFaceEnabled

    /**
     * Resets all cached state to unknown values, forcing re-initialization.
     * Use this when external code (e.g., ImGui) may have modified OpenGL state.
     */
    fun invalidateCache() {
        GlPrograms.invalidate()
        blendEnabled = glIsEnabled(GL_BLEND)
        depthTestEnabled = glIsEnabled(GL_DEPTH_TEST)
        depthMaskEnabled = glGetBoolean(GL_DEPTH_WRITEMASK)
        cullFaceEnabled = glIsEnabled(GL_CULL_FACE)
        depthFunc = glGetInteger(GL_DEPTH_FUNC)
        readBlendFactors()
    }

    /**
     * Applies a complete state reset to OpenGL defaults.
     * Useful at the start of a frame or after external state modifications.
     */
    fun resetToDefaults() {
        setDepthTestEnabled(true)
        setDepthMask(true)
        setDepthFunc(GL11.GL_LESS)
        setBlendEnabled(false)
        setCullFaceEnabled(false)
    }
}

/** Pure bookkeeping for the cached blend factors ([GLStateTracker]); kept GL-free for unit tests. */
internal object BlendFactors {
    fun matches(cache: IntArray, srcRgb: Int, dstRgb: Int, srcAlpha: Int, dstAlpha: Int): Boolean =
        cache[0] == srcRgb && cache[1] == dstRgb && cache[2] == srcAlpha && cache[3] == dstAlpha

    fun store(cache: IntArray, srcRgb: Int, dstRgb: Int, srcAlpha: Int, dstAlpha: Int) {
        cache[0] = srcRgb; cache[1] = dstRgb; cache[2] = srcAlpha; cache[3] = dstAlpha
    }
}
