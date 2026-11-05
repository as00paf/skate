package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RenderTargetDesc
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.Wrap

/**
 * Layered depth shadow map: a depth-only 2D array target with one layer per cascade (directional light) or per
 * spot light / point light cube face. Render a layer with `beginPass(target, layer, ClearValues.DEPTH)`.
 *
 * The texture uses hardware depth comparison (`sampler2DArrayShadow`) with linear filtering, so every tap is a
 * 2x2 bilinear PCF sample; the shader adds a wider kernel depending on [com.pafoid.skate.engine.render.shadow.ShadowQualityLevel].
 *
 * Storage is (re)allocated by [ensure] when the quality tier changes, never per frame.
 */
class ShadowMap(
    resolution: Int = 2048,
    layers: Int = 1,
    private val device: () -> RhiDevice = RhiContext::require,
) {
    var resolution: Int = resolution
        private set
    var layers: Int = layers
        private set

    val width: Int get() = resolution
    val height: Int get() = resolution

    /** The RHI target, null until [ensure] allocates it. */
    var target: RhiRenderTarget? = null
        private set

    fun getDepthTextureId(): Int = target?.depthTexture()?.nativeHandle?.toInt() ?: 0

    fun isInitialized(): Boolean = target != null

    /** Reallocates storage if the size or layer count changed (clamped to the device's limits). Returns true when it did. */
    fun ensure(resolution: Int, layers: Int): Boolean {
        val caps = device().caps
        val res = minOf(resolution, caps.maxTextureSize)
        val count = minOf(layers.coerceAtLeast(1), caps.maxArrayLayers)
        if (target != null && res == this.resolution && count == this.layers) return false
        this.resolution = res
        this.layers = count
        target?.close()
        target = device().createRenderTarget(desc(res, count))
        return true
    }

    fun destroy() {
        target?.close()
        target = null
    }

    companion object {
        fun desc(resolution: Int, layers: Int) = RenderTargetDesc(
            resolution, resolution,
            colors = emptyList(),
            depth = PixelFormat.DEPTH32F,
            layers = layers,
            layered = true,
            depthSampler = SamplerDesc(Filter.LINEAR, Filter.LINEAR, Wrap.CLAMP_TO_EDGE, compare = CompareFunc.LESS_EQUAL),
        )
    }
}
