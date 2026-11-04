package com.pafoid.skate.engine.render

import org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_LEQUAL
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_MAX_TEXTURE_SIZE
import org.lwjgl.opengl.GL11.GL_NONE
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.glClear
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL12.glTexImage3D
import org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_FUNC
import org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE
import org.lwjgl.opengl.GL20.glDrawBuffer
import org.lwjgl.opengl.GL20.glReadBuffer
import org.lwjgl.opengl.GL30.GL_COMPARE_REF_TO_TEXTURE
import org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT32F
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE
import org.lwjgl.opengl.GL30.GL_MAX_ARRAY_TEXTURE_LAYERS
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glBindTexture
import org.lwjgl.opengl.GL30.glCheckFramebufferStatus
import org.lwjgl.opengl.GL30.glDeleteFramebuffers
import org.lwjgl.opengl.GL30.glFramebufferTextureLayer
import org.lwjgl.opengl.GL30.glGenFramebuffers

/**
 * Layered depth shadow map: a `GL_TEXTURE_2D_ARRAY` with one layer per cascade (directional light) or per
 * spot light / point light cube face.
 *
 * The texture uses hardware depth comparison (`sampler2DArrayShadow`) with linear filtering, so every tap is a
 * 2x2 bilinear PCF sample; the shader adds a wider kernel depending on [com.pafoid.skate.engine.render.shadow.ShadowQualityLevel].
 *
 * Storage is (re)allocated by [ensure] when the quality tier changes, never per frame.
 */
class ShadowMap(resolution: Int = 2048, layers: Int = 1) {
    var resolution: Int = resolution
        private set
    var layers: Int = layers
        private set

    val width: Int get() = resolution
    val height: Int get() = resolution

    private var fboId = 0
    private var depthTextureId = 0
    private var initialized = false

    fun getDepthTextureId(): Int = depthTextureId

    fun isInitialized(): Boolean = initialized

    fun initialize() {
        if (initialized) return
        fboId = glGenFramebuffers()
        allocate()
        initialized = true
    }

    /** Reallocates storage if the size or layer count changed. Returns true when it did. */
    fun ensure(resolution: Int, layers: Int): Boolean {
        val res = minOf(resolution, glGetInteger(GL_MAX_TEXTURE_SIZE))
        val count = minOf(layers.coerceAtLeast(1), glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS))
        if (initialized && res == this.resolution && count == this.layers) return false
        this.resolution = res
        this.layers = count
        if (!initialized) initialize() else allocate()
        return true
    }

    private fun allocate() {
        if (depthTextureId != 0) glDeleteTextures(depthTextureId)
        depthTextureId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D_ARRAY, depthTextureId)
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_DEPTH_COMPONENT32F, resolution, resolution, layers, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0L)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE)
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL)
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0)

        glBindFramebuffer(GL_FRAMEBUFFER, fboId)
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTextureId, 0, 0)
        glDrawBuffer(GL_NONE)
        glReadBuffer(GL_NONE)
        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        check(status == GL_FRAMEBUFFER_COMPLETE) { "Shadow map framebuffer is not complete. Status: $status" }
    }

    /** Binds the framebuffer to render into [layer], sets the viewport and clears depth. */
    fun bindLayer(layer: Int) {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTextureId, 0, layer)
        glViewport(0, 0, resolution, resolution)
        glClear(GL_DEPTH_BUFFER_BIT)
    }

    fun unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

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
