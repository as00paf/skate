package com.pafoid.skate.engine.render.utils

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glBlendFunc
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

    // Blend function cache
    private var blendSrcFactor: Int = GL11.GL_SRC_ALPHA
    private var blendDstFactor: Int = GL11.GL_ONE_MINUS_SRC_ALPHA

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
        }
    }

    /**
     * Returns whether blending is currently enabled.
     */
    fun isBlendEnabled(): Boolean = blendEnabled

    /**
     * Sets the blend function, only making the GL call if state changes.
     */
    fun setBlendFunc(sfactor: Int, dfactor: Int) {
        if (blendSrcFactor != sfactor || blendDstFactor != dfactor) {
            glBlendFunc(sfactor, dfactor)
            blendSrcFactor = sfactor
            blendDstFactor = dfactor
        }
    }

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
        blendEnabled = glIsEnabled(GL_BLEND)
        depthTestEnabled = glIsEnabled(GL_DEPTH_TEST)
        depthMaskEnabled = glGetBoolean(GL_DEPTH_WRITEMASK)
        cullFaceEnabled = glIsEnabled(GL_CULL_FACE)
        depthFunc = glGetInteger(GL_DEPTH_FUNC)
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
