package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RenderTargetDesc
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.Wrap

/** Colour-only render target (RGBA8, no depth) used as post-processing scratch space. Created lazily by [ensure]. */
class ColorTarget(
    private val device: () -> RhiDevice = RhiContext::require,
    private val msaaSamples: Int = 0,
) {
    var target: RhiRenderTarget? = null
        private set
    val width: Int get() = target?.width ?: 0
    val height: Int get() = target?.height ?: 0

    /** Native handle of the color texture, 0 before [ensure]. */
    val textureId: Int get() = target?.colorTexture()?.nativeHandle?.toInt() ?: 0

    /** (Re)allocates the target when its size changes; no-op otherwise. */
    fun ensure(width: Int, height: Int) {
        val current = target
        if (current == null) target = device().createRenderTarget(desc(width, height))
        else current.resize(width, height)
    }

    fun destroy() {
        target?.close()
        target = null
    }

    companion object {
        fun desc(width: Int, height: Int) = RenderTargetDesc(
            width, height,
            colors = listOf(PixelFormat.RGBA8),
            depth = null,
            colorSampler = SamplerDesc(Filter.LINEAR, Filter.LINEAR, Wrap.CLAMP_TO_EDGE),
        )
    }
}
