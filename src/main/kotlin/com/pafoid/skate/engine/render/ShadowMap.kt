package com.pafoid.skate.engine.render

import org.lwjgl.opengl.GL30.GL_CLAMP_TO_BORDER
import org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL30.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT
import org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT32F
import org.lwjgl.opengl.GL30.GL_FLOAT
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE
import org.lwjgl.opengl.GL30.GL_LINEAR
import org.lwjgl.opengl.GL30.GL_MAX_TEXTURE_SIZE
import org.lwjgl.opengl.GL30.GL_NONE
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D
import org.lwjgl.opengl.GL30.GL_TEXTURE_BORDER_COLOR
import org.lwjgl.opengl.GL30.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL30.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glBindTexture
import org.lwjgl.opengl.GL30.glCheckFramebufferStatus
import org.lwjgl.opengl.GL30.glClear
import org.lwjgl.opengl.GL30.glDeleteFramebuffers
import org.lwjgl.opengl.GL30.glDeleteTextures
import org.lwjgl.opengl.GL30.glDrawBuffers
import org.lwjgl.opengl.GL30.glFramebufferTexture2D
import org.lwjgl.opengl.GL30.glGenFramebuffers
import org.lwjgl.opengl.GL30.glGenTextures
import org.lwjgl.opengl.GL30.glGetInteger
import org.lwjgl.opengl.GL30.glTexImage2D
import org.lwjgl.opengl.GL30.glTexParameterfv
import org.lwjgl.opengl.GL30.glViewport
import org.lwjgl.opengl.GL32.glTexParameteri

/**
 * Shadow map framebuffer for directional light shadow mapping.
 *
 * Creates a depth-only framebuffer that renders scene depth from the light's perspective.
 * The depth texture is later sampled in the main PBR shader to determine shadow coverage.
 *
 * ## Configuration
 *
 * - **Resolution**: Configurable (default: 2048x2048, max: GPU-dependent)
 * - **Depth Format**: GL_DEPTH_COMPONENT32F (32-bit float depth)
 * - **Filtering**: GL_LINEAR for PCF sampling
 * - **Wrapping**: GL_CLAMP_TO_BORDER with border color (1,1,1,1) for shadow bias handling
 *
 * @param width Shadow map width in pixels (default: 2048)
 * @param height Shadow map height in pixels (default: 2048)
 */
class ShadowMap(
    val width: Int = 2048,
    val height: Int = 2048
) {
    companion object {
        /**
         * Queries the GPU for the maximum supported 2D texture size.
         * This can be used to determine the maximum shadow map resolution.
         *
         * @return Maximum texture size in pixels (typically 8192, 16384, or 32768)
         */
        fun getMaxShadowMapResolution(): Int {
            return glGetInteger(GL_MAX_TEXTURE_SIZE)
        }

        /**
         * Creates a ShadowMap with the highest supported resolution up to the specified maximum.
         *
         * @param desiredResolution Desired resolution (default: 4096)
         * @return ShadowMap with the best supported resolution
         */
        fun createWithBestResolution(desiredResolution: Int = 4096): ShadowMap {
            val maxSupported = getMaxShadowMapResolution()
            val resolution = minOf(desiredResolution, maxSupported)
            return ShadowMap(resolution, resolution)
        }
    }
    private var fboId = 0
    private var depthTextureId = 0
    private var initialized = false

    /**
     * Gets the OpenGL framebuffer object ID.
     * Returns 0 if not initialized.
     */
    fun getFboId(): Int = fboId

    /**
     * Gets the depth texture ID for sampling in shaders.
     * Returns 0 if not initialized.
     */
    fun getDepthTextureId(): Int = depthTextureId

    /**
     * Returns true if the shadow map has been initialized.
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Initializes the shadow map framebuffer and depth texture.
     *
     * Configures:
     * - Framebuffer with depth attachment only (no color buffer)
     * - 32-bit float depth texture for high precision
     * - Linear filtering for PCF shadow sampling
     * - Clamp to border with white border color (depth = 1.0 = always in shadow)
     */
    fun initialize() {
        if (initialized) return

        // Generate framebuffer
        fboId = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)

        // Create depth texture
        depthTextureId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, depthTextureId)

        // Allocate storage for depth texture
        glTexImage2D(
            GL_TEXTURE_2D,
            0,
            GL_DEPTH_COMPONENT32F,
            width,
            height,
            0,
            GL_DEPTH_COMPONENT,
            GL_FLOAT,
            0
        )

        // Set texture parameters
        // Linear filtering for PCF sampling
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        // Clamp to border to prevent shadow artifacts at edges
        // Border color (1,1,1,1) means depth = 1.0 (always in shadow when outside)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER)

        // Set border color to white (depth = 1.0)
        val borderColor = floatArrayOf(1f, 1f, 1f, 1f)
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, borderColor)

        // Attach depth texture to framebuffer
        glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_DEPTH_ATTACHMENT,
            GL_TEXTURE_2D,
            depthTextureId,
            0
        )

        // Disable color buffers (depth-only rendering)
        val drawBuffers = intArrayOf(GL_NONE)
        glDrawBuffers(drawBuffers)

        // Verify framebuffer is complete
        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw IllegalStateException("Shadow map framebuffer is not complete. Status: $status")
        }

        // Unbind
        glBindTexture(GL_TEXTURE_2D, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)

        initialized = true
    }

    /**
     * Binds the shadow map framebuffer for rendering.
     * Sets viewport to shadow map resolution.
     *
     * Call this before rendering the scene from the light's perspective.
     */
    fun bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)
        glViewport(0, 0, width, height)
    }

    /**
     * Unbinds the shadow map framebuffer.
     * Restores default framebuffer binding.
     *
     * Call this after rendering the shadow pass to return to normal rendering.
     */
    fun unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    /**
     * Clears the depth buffer in preparation for shadow rendering.
     *
     * Call this after bind() and before rendering the scene.
     */
    fun clear() {
        glClear(GL_DEPTH_BUFFER_BIT)
    }

    /**
     * Destroys the shadow map and frees OpenGL resources.
     *
     * Call this when the shadow map is no longer needed (e.g., on shutdown).
     */
    fun destroy() {
        if (fboId != 0) {
            glDeleteFramebuffers(fboId)
            fboId = 0
        }
        if (depthTextureId != 0) {
            glDeleteTextures(depthTextureId)
            depthTextureId = 0
        }
        initialized = false
    }
}
