package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.data.Texture
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_REPEAT
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT
import org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT32
import org.lwjgl.opengl.GL30.GL_FLOAT
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE
import org.lwjgl.opengl.GL30.GL_NEAREST
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D
import org.lwjgl.opengl.GL30.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL30.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glBindTexture
import org.lwjgl.opengl.GL30.glCheckFramebufferStatus
import org.lwjgl.opengl.GL30.glDeleteFramebuffers
import org.lwjgl.opengl.GL30.glFramebufferTexture2D
import org.lwjgl.opengl.GL30.glGenFramebuffers
import org.lwjgl.opengl.GL30.glGenTextures
import org.lwjgl.opengl.GL30.glTexImage2D
import org.lwjgl.opengl.GL30.glTexParameteri
import java.nio.ByteBuffer

class FrameBuffer(var width: Int, var height: Int) {
    var fboId = 0
    private var depthTexture: Int = 0
    private var texture: Texture = Texture()

    fun initialize() {
        // Generate frame buffer
        fboId = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)

        // Create the texture to render the data to, and attach it to our framebuffer
        texture = Texture(width, height)
        texture.texId = glGenTextures()
        texture.width = width
        texture.height = height
        texture.depth = 1
        texture.filePath = "Generated::${texture.texId}"

        glBindTexture(GL_TEXTURE_2D, texture.texId)

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)

        glTexImage2D(
            GL_TEXTURE_2D, 0, GL_RGBA, width, height,
            0, GL_RGBA, GL_UNSIGNED_BYTE, null as ByteBuffer?
        )

        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture.texId, 0)

        // Create depth texture
        depthTexture = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, depthTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0)

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            assert(false) { "Error: Framebuffer is not complete" }
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    /**
     * Resizes the framebuffer to the specified dimensions.
     * Recreates the color and depth textures with the new size.
     */
    fun resize(width: Int, height: Int) {
        if (width == this.width && height == this.height) return

        this.width = width
        this.height = height

        // Cleanup old resources
        if (fboId != 0) {
            glDeleteFramebuffers(fboId)
        }
        if (depthTexture != 0) {
            glDeleteTextures(depthTexture)
        }
        if (texture.texId != -1) {
            glDeleteTextures(texture.texId)
        }

        // Recreate with new dimensions
        initialize()
    }

    fun bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)
    }

    fun unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun getTextureId() = texture.texId

    fun destroy() {
        if (fboId != 0) {
            glDeleteFramebuffers(fboId)
            fboId = 0
        }
        if (depthTexture != 0) {
            GL30.glDeleteTextures(depthTexture)
            depthTexture = 0
        }
        if (texture.texId != 0) {
            glDeleteTextures(texture.texId)
        }
    }
}
