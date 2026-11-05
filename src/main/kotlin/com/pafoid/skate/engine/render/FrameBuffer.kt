package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RenderTargetDesc
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.Wrap

/**
 * The scene's render target: RGBA8 color (sampled by post-processing and shown in the editor viewport) plus a
 * DEPTH32F depth attachment (read by SSAO). Allocated by [initialize].
 */
class FrameBuffer(
    width: Int,
    height: Int,
    private val device: () -> RhiDevice = RhiContext::require,
) {
    var width: Int = width
        private set
    var height: Int = height
        private set

    private var renderTarget: RhiRenderTarget? = null

    /** The RHI target; [initialize] first. */
    val target: RhiRenderTarget get() = checkNotNull(renderTarget) { "FrameBuffer is not initialized" }

    fun initialize() {
        renderTarget?.close()
        renderTarget = device().createRenderTarget(desc(width, height))
    }

    /** Resizes the attachments (new contents); no-op when the size is unchanged. */
    fun resize(width: Int, height: Int) {
        if (width == this.width && height == this.height) return
        this.width = width
        this.height = height
        renderTarget?.resize(width, height) ?: initialize()
    }

    /** Native handle of the color texture (ImGui and draw code not yet on the RHI). */
    fun getTextureId(): Int = renderTarget?.colorTexture()?.nativeHandle?.toInt() ?: 0

    fun getDepthTextureId(): Int = renderTarget?.depthTexture()?.nativeHandle?.toInt() ?: 0

    fun destroy() {
        renderTarget?.close()
        renderTarget = null
    }

    companion object {
        fun desc(width: Int, height: Int) = RenderTargetDesc(
            width, height,
            colors = listOf(PixelFormat.RGBA8),
            depth = PixelFormat.DEPTH32F,
            colorSampler = SamplerDesc(Filter.LINEAR, Filter.LINEAR, Wrap.REPEAT),
            depthSampler = SamplerDesc(Filter.NEAREST, Filter.NEAREST, Wrap.REPEAT),
        )
    }
}
