package com.pafoid.skate.engine.render.lighting

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-27: the sky bake (display texels -> radiance -> prefiltered levels + SH) and the BRDF table texture. */
class EnvironmentBakeTest {
    private val w = EnvironmentBake.BASE_WIDTH
    private val h = EnvironmentBake.BASE_HEIGHT

    private fun display(r: Int, g: Int, b: Int) = ByteArray(w * h * 4).also { px ->
        for (i in 0 until w * h) { px[i * 4] = r.toByte(); px[i * 4 + 1] = g.toByte(); px[i * 4 + 2] = b.toByte(); px[i * 4 + 3] = -1 }
    }

    @Test
    fun radiance_DecodesAboveTheHorizonAndFadesToFogBelow() {
        val fog = Vector3f(0.5f, 0.5f, 0.5f)
        val image = EnvironmentBake.radiance(display(51, 102, 204), w, h, Vector3f(1f), 1f, fog)
        fun red(row: Int) = image.rgb[(row * w) * 3]
        assertEquals(IblMath.decode(0.2f), red(h - 1), 1e-3f, "sky texel")
        assertEquals(IblMath.decode(0.5f), red(0), 1e-3f, "fog below the horizon, like the dome")
    }

    @Test
    fun radiance_AppliesTintAndExposureLikeTheDome() {
        val image = EnvironmentBake.radiance(display(100, 100, 100), w, h, Vector3f(1f, 0.5f, 1f), 1.5f, Vector3f())
        val top = (h - 1) * w * 3
        assertEquals(IblMath.decode(100 / 255f * 1.5f), image.rgb[top], 1e-3f)
        assertEquals(IblMath.decode(100 / 255f * 0.75f), image.rgb[top + 1], 1e-3f)
    }

    @Test
    fun bake_GivesEveryLevelAtItsSize_AndAConstantSkyStaysConstant() {
        val sky = EnvironmentBake.radiance(display(128, 128, 128), w, h, Vector3f(1f), 1f, Vector3f(128 / 255f))
        val baked = EnvironmentBake.bake(sky)
        assertEquals(EnvironmentBake.LEVELS, baked.levels.size)
        baked.levels.forEachIndexed { level, bytes ->
            assertEquals(EnvironmentBake.width(level) * EnvironmentBake.height(level) * 4, bytes.size)
            for (i in 0 until bytes.size / 4) assertEquals(128, bytes[i * 4].toInt() and 0xFF, "level $level texel $i")
        }
        val e = IblMath.shIrradiance(baked.shIrradiance, Vector3f(0f, 1f, 0f), Vector3f())
        assertEquals(Math.PI.toFloat() * IblMath.decode(128 / 255f), e.x, 0.05f)
    }

    @Test
    fun levels_MapRoughnessLinearly() {
        assertEquals(0f, EnvironmentBake.roughnessOf(0))
        assertEquals(1f, EnvironmentBake.roughnessOf(EnvironmentBake.LEVELS - 1))
        assertEquals(256, EnvironmentBake.width(0)); assertEquals(8, EnvironmentBake.width(5)); assertEquals(4, EnvironmentBake.height(5))
    }

    @Test
    fun brdfLutTexture_HoldsAAndBInRedAndGreen() {
        val lut = EnvironmentBake.brdfLutRgba8
        val n = EnvironmentBake.LUT_SIZE
        assertEquals(n * n * 4, lut.size)
        val headOnSmooth = (0 * n + (n - 1)) * 4 // NdotV ~ 1, roughness ~ 0
        assertTrue((lut[headOnSmooth].toInt() and 0xFF) > 230, "A ~ 1")
        assertTrue((lut[headOnSmooth + 1].toInt() and 0xFF) < 15, "B ~ 0")
    }
}
