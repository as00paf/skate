package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * REN-24: the FXAA effect blurred the whole frame because its shader blended every pixel with its four diagonal
 * neighbours (a box blur at 90% strength). FXAA now only touches pixels on a contrast edge.
 */
class FxaaEdgeTest {
    private fun flat(luma: Float) = FloatArray(9) { luma }

    @Test
    fun flatArea_IsNotAnEdge() {
        assertFalse(FxaaEdge.isEdge(flat(0.5f)))
        assertFalse(FxaaEdge.isEdge(flat(0f)))
        assertFalse(FxaaEdge.isEdge(flat(1f)))
    }

    @Test
    fun lowContrastTextureDetail_IsNotAnEdge() {
        // Asphalt-like grain: +-4% around mid grey stays sharp
        assertFalse(FxaaEdge.isEdge(floatArrayOf(0.48f, 0.52f, 0.5f, 0.47f, 0.5f, 0.53f, 0.49f, 0.51f, 0.5f)))
    }

    @Test
    fun darkNoise_BelowTheAbsoluteThreshold_IsNotAnEdge() {
        assertFalse(FxaaEdge.isEdge(0.01f, 0.03f))
    }

    @Test
    fun hardEdge_IsAnEdge() {
        assertTrue(FxaaEdge.isEdge(floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)))
        assertTrue(FxaaEdge.isEdge(0.2f, 0.6f))
    }

    @Test
    fun threshold_ScalesWithTheBrightestNeighbour() {
        // A 0.1 step is an edge in mid tones (needs 0.125 * 0.5 = 0.0625) but not near white (needs 0.125)
        assertTrue(FxaaEdge.isEdge(0.4f, 0.5f))
        assertFalse(FxaaEdge.isEdge(0.9f, 1.0f))
    }

    @Test
    fun luma_UsesPerceptualWeights() {
        assertTrue(FxaaEdge.luma(0f, 1f, 0f) > FxaaEdge.luma(1f, 0f, 0f))
        assertTrue(FxaaEdge.luma(1f, 0f, 0f) > FxaaEdge.luma(0f, 0f, 1f))
        assertTrue(kotlin.math.abs(FxaaEdge.luma(1f, 1f, 1f) - 1f) < 1e-5f)
    }

    @Test
    fun shader_ReadsTheThresholdsAndSkipsNonEdgePixels() {
        val source = checkNotNull(javaClass.getResource("/shaders/post_fxaa.glsl")).readText()
        assertTrue("uniform float ${Uniforms.FXAA_EDGE_THRESHOLD};" in source)
        assertTrue("uniform float ${Uniforms.FXAA_EDGE_THRESHOLD_MIN};" in source)
        // Non-edge pixels return the centre sample unchanged
        assertTrue(Regex("""if \(lumaMax - lumaMin < max\(""").containsMatchIn(source))
        assertTrue("color = vec4(rgbM, 1.0);" in source)
    }
}
