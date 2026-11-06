package com.pafoid.skate.engine.render.renderer.passes

/**
 * Edge test of the FXAA post effect (`post_fxaa.glsl`), kept in Kotlin so it can be unit-tested (REN-24).
 *
 * FXAA only touches pixels on a contrast edge: the luma range of the pixel and its 8 neighbours must reach
 * `max(EDGE_THRESHOLD_MIN, lumaMax * EDGE_THRESHOLD)`. Every other pixel is returned unchanged. The previous shader
 * blended every pixel with its diagonal neighbours, which blurred the whole frame. The pass uploads these thresholds
 * to the shader, so the shader and this test use the same values.
 */
object FxaaEdge {
    /** Minimum local contrast relative to the brightest neighbour (1/8: FXAA "high quality"). */
    const val EDGE_THRESHOLD = 1f / 8f

    /** Minimum absolute local contrast, so dark noise is left alone (1/32). */
    const val EDGE_THRESHOLD_MIN = 1f / 32f

    /** Perceptual luma of a linear 0..1 colour (same weights as the shader). */
    fun luma(r: Float, g: Float, b: Float): Float = 0.299f * r + 0.587f * g + 0.114f * b

    /** True when a pixel whose 3x3 neighbourhood has lumas in [lumaMin]..[lumaMax] is anti-aliased. */
    fun isEdge(lumaMin: Float, lumaMax: Float): Boolean =
        lumaMax - lumaMin >= maxOf(EDGE_THRESHOLD_MIN, lumaMax * EDGE_THRESHOLD)

    /** [isEdge] over the lumas of a 3x3 neighbourhood. */
    fun isEdge(neighbourhood: FloatArray): Boolean {
        require(neighbourhood.size == 9) { "a 3x3 neighbourhood has 9 lumas, got ${neighbourhood.size}" }
        return isEdge(neighbourhood.min(), neighbourhood.max())
    }
}
