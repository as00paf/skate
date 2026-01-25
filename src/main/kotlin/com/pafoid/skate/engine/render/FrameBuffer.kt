package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.Texture
import org.lwjgl.opengl.GL30.*

class FrameBuffer(val width: Int, val height: Int) {
    private var fboId = 0
    private var texture: Texture

    init {
        // Generate frame buffer
        fboId = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)

        // Create the texture to render the data to, and attach it to our framebuffer
        texture = Texture().init(width, height)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture.getId(), 0)

        // Create renderbuffer to store the depth info
        val rboId = glGenRenderbuffers()
        glBindRenderbuffer(GL_RENDERBUFFER, rboId)
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT32, width, height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, rboId)

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            assert(false) { "Error: Framebuffer is not complete" }
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)
    }

    fun unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun getTextureId() = texture.getId()
    fun getFboId() = fboId
}