package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.utils.EntityIdEncoder
import org.lwjgl.opengl.GL11.glReadPixels
import org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT
import org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT32F
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_FLOAT
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE
import org.lwjgl.opengl.GL30.GL_NEAREST
import org.lwjgl.opengl.GL30.GL_NONE
import org.lwjgl.opengl.GL30.GL_PACK_ALIGNMENT
import org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_REPEAT
import org.lwjgl.opengl.GL30.GL_RGB
import org.lwjgl.opengl.GL30.GL_RGB32F
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D
import org.lwjgl.opengl.GL30.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL30.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL30.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glBindTexture
import org.lwjgl.opengl.GL30.glCheckFramebufferStatus
import org.lwjgl.opengl.GL30.glDeleteFramebuffers
import org.lwjgl.opengl.GL30.glDeleteTextures
import org.lwjgl.opengl.GL30.glDrawBuffer
import org.lwjgl.opengl.GL30.glFinish
import org.lwjgl.opengl.GL30.glFramebufferTexture2D
import org.lwjgl.opengl.GL30.glGenFramebuffers
import org.lwjgl.opengl.GL30.glGenTextures
import org.lwjgl.opengl.GL30.glPixelStorei
import org.lwjgl.opengl.GL30.glReadBuffer
import org.lwjgl.opengl.GL30.glReadPixels
import org.lwjgl.opengl.GL30.glTexImage2D
import org.lwjgl.opengl.GL30.glTexParameteri

/**
 * Picking texture for object selection via GPU rendering.
 *
 * This class implements a render-to-texture technique for object picking (selection/hover
 * detection). Objects are rendered to this texture with their entity ID encoded as color
 * values, allowing CPU-side identification of which object is under the mouse cursor.
 *
 * ## Coordinate Systems
 *
 * Understanding the coordinate systems is crucial for correct usage:
 *
 * ### Screen Space (Input Coordinates)
 * - Origin: Top-left corner
 * - X: 0 to width-1 (left to right)
 * - Y: 0 to height-1 (top to bottom)
 * - Used by: Mouse input, windowing systems
 *
 * ### OpenGL Texture Space
 * - Origin: Bottom-left corner
 * - X: 0 to width-1 (left to right)
 * - Y: 0 to height-1 (bottom to top)
 * - Used by: OpenGL texture sampling, [glReadPixels]
 *
 * ### Y-Axis Inversion
 *
 * When reading picking data, you must convert from screen space to texture space:
 * ```kotlin
 * val textureY = screenHeight - 1 - screenY
 * val entityId = pickingTexture.readPixel(mouseX, textureY)
 * ```
 *
 * This inversion is necessary because:
 * 1. Screen coordinates have Y=0 at the top (windowing convention)
 * 2. OpenGL texture coordinates have Y=0 at the bottom (OpenGL convention)
 * 3. [glReadPixels] expects texture-space coordinates
 *
 * ## Usage Example
 *
 * ```kotlin
 * val pickingTexture = PickingTexture(1920, 1080)
 *
 * // During render pass:
 * pickingTexture.enableWriting()
 * // ... render objects with encoded IDs ...
 * pickingTexture.disableWriting()
 *
 * // Reading picked object:
 * val screenX = mouseX
 * val screenY = mouseY
 * val textureY = windowHeight - 1 - screenY
 * val entityId = pickingTexture.readPixel(screenX, textureY)
 * ```
 *
 * @param width Initial texture width (typically matches viewport width)
 * @param height Initial texture height (typically matches viewport height)
 */
class PickingTexture(private var width: Int, private var height: Int) {

    private var pickingTextureId: Int = 0
    private var fbo: Int = 0
    private var depthTexture: Int = 0

    init {
        if (!init()) assert(false) { "Error initializing picking texture" }
    }

    fun resize(width: Int, height: Int) {
        if (this.width == width && this.height == height) return

        this.width = width
        this.height = height

        // Cleanup old resources
        glDeleteFramebuffers(fbo)
        glDeleteTextures(pickingTextureId)
        glDeleteTextures(depthTexture)

        init()
    }

    fun init(): Boolean {
        // Generate frame buffer
        fbo = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)

        // Create the texture to render the data to, and attach it to our framebuffer
        pickingTextureId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, pickingTextureId)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB32F, width, height, 0, GL_RGB, GL_FLOAT, 0)

        glFramebufferTexture2D(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D,
            pickingTextureId,
            0
        )

        // Create the texture object for the depth buffer
        depthTexture = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, depthTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, width, height, 0,
            GL_DEPTH_COMPONENT, GL_FLOAT, 0)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
            GL_TEXTURE_2D, depthTexture, 0)

        // Disable the reading
        glReadBuffer(GL_NONE)
        glDrawBuffer(GL_COLOR_ATTACHMENT0)

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            assert(false) { "Error: Framebuffer is not complete" }
            return false
        }

        // Unbind the texture and framebuffer
        glBindTexture(GL_TEXTURE_2D, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)

        return true
    }

    fun enableWriting() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo)
    }

    fun disableWriting() {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0)
    }

    /**
     * Reads a pixel value from the picking texture at the specified coordinates.
     *
     * **Important**: Coordinates must be in OpenGL texture space (Y=0 at bottom),
     * NOT screen space (Y=0 at top). If you have screen-space coordinates (e.g., from
     * mouse input), you must invert the Y coordinate:
     *
     * ```kotlin
     * // Screen-space mouse coordinates
     * val screenX = mouseX
     * val screenY = mouseY
     *
     * // Convert to texture-space for readPixel
     * val textureY = textureHeight - 1 - screenY
     * val entityId = pickingTexture.readPixel(screenX, textureY)
     * ```
     *
     * @param x The X coordinate in **texture space** (0 to width-1, left to right).
     * @param y The Y coordinate in **texture space** (0 to height-1, bottom to top).
     *          NOT screen space! Invert screen Y using: `textureY = height - 1 - screenY`.
     * @return The encoded entity ID at the specified pixel, or -1 if no entity.
     *
     * @see EntityIdEncoder for ID encoding/decoding details
     */
    fun readPixel(x: Int, y: Int): Int {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo)
        glReadBuffer(GL_COLOR_ATTACHMENT0)

        glFinish() // Wait for GPU to finish rendering to ensure we read fresh data
        glPixelStorei(GL_PACK_ALIGNMENT, 1)
        val pixels = FloatArray(3)
        glReadPixels(x, y, 1, 1, GL_RGB, GL_FLOAT, pixels)

        return EntityIdEncoder.decode(pixels[0])
    }

    fun destroy() {
        if (fbo != 0) {
            glDeleteFramebuffers(fbo)
        }
        if (pickingTextureId != 0) {
            glDeleteTextures(pickingTextureId)
        }
        if (depthTexture != 0) {
            glDeleteTextures(depthTexture)
        }
    }
}