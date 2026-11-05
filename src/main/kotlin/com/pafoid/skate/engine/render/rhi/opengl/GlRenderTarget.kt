package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.rhi.RenderTargetDesc
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.RhiTexture
import org.lwjgl.opengl.GL11.GL_NONE
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.glDrawBuffer
import org.lwjgl.opengl.GL11.glReadBuffer
import org.lwjgl.opengl.GL20.glDrawBuffers
import org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glCheckFramebufferStatus
import org.lwjgl.opengl.GL30.glDeleteFramebuffers
import org.lwjgl.opengl.GL30.glFramebufferTexture2D
import org.lwjgl.opengl.GL30.glFramebufferTextureLayer
import org.lwjgl.opengl.GL30.glGenFramebuffers

/**
 * A framebuffer object with its attachment textures. A layered target attaches one layer at a time
 * ([attachLayer], called by `beginPass`).
 */
internal class GlRenderTarget(
    desc: RenderTargetDesc,
    private val anisotropyAvailable: Boolean,
) : RhiRenderTarget {
    override var desc: RenderTargetDesc = desc
        private set
    override val width: Int get() = desc.width
    override val height: Int get() = desc.height

    var fbo = 0
        private set
    private var colors: List<GlTexture> = emptyList()
    private var depth: GlTexture? = null
    private var attachedLayer = 0
    private var closed = false

    init {
        allocate()
    }

    override fun colorTexture(index: Int): RhiTexture = colors[index]

    override fun depthTexture(): RhiTexture? = depth

    override fun resize(width: Int, height: Int) {
        if (width == desc.width && height == desc.height) return
        desc = desc.copy(width = width, height = height)
        release()
        allocate()
    }

    /** Attaches [layer] of every attachment when the target is layered and another layer is attached. */
    fun attachLayer(layer: Int) {
        if (!desc.layered || layer == attachedLayer) return
        require(layer in 0 until desc.layers) { "Layer $layer outside 0..${desc.layers - 1}" }
        attachAll(layer)
    }

    private fun allocate() {
        colors = desc.colors.indices.map { i -> GlTexture(desc.colorTexture(i), anisotropyAvailable).also { it.upload(0, 0, null) } }
        depth = desc.depthTexture()?.let { GlTexture(it, anisotropyAvailable).also { t -> t.upload(0, 0, null) } }

        fbo = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        attachAll(0)
        when {
            colors.isEmpty() -> {
                glDrawBuffer(GL_NONE)
                glReadBuffer(GL_NONE)
            }
            colors.size > 1 -> glDrawBuffers(IntArray(colors.size) { GL_COLOR_ATTACHMENT0 + it })
        }
        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        check(status == GL_FRAMEBUFFER_COMPLETE) { "Framebuffer for $desc is not complete. Status: $status" }
    }

    /** Needs [fbo] bound to GL_FRAMEBUFFER. */
    private fun attachAll(layer: Int) {
        colors.forEachIndexed { i, t -> attach(GL_COLOR_ATTACHMENT0 + i, t, layer) }
        depth?.let { attach(GL_DEPTH_ATTACHMENT, it, layer) }
        attachedLayer = layer
    }

    private fun attach(attachment: Int, texture: GlTexture, layer: Int) {
        if (desc.layered) glFramebufferTextureLayer(GL_FRAMEBUFFER, attachment, texture.id, 0, layer)
        else glFramebufferTexture2D(GL_FRAMEBUFFER, attachment, GL_TEXTURE_2D, texture.id, 0)
    }

    private fun release() {
        if (fbo != 0) glDeleteFramebuffers(fbo)
        fbo = 0
        colors.forEach { it.close() }
        depth?.close()
        colors = emptyList()
        depth = null
    }

    override fun close() {
        if (closed) return
        closed = true
        release()
    }
}
