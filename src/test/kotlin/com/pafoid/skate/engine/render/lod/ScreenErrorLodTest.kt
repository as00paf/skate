package com.pafoid.skate.engine.render.lod

import com.pafoid.skate.engine.render.water.WaterLod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-29 §6.1: screen-error LOD shared by water and terrain. */
class ScreenErrorLodTest {
    private val scale = ScreenErrorLod.projectionScale(1080, 45f)

    @Test
    fun ProjectionScale_IsHalfHeightOverTanHalfFov() {
        assertEquals(1080f / (2f * kotlin.math.tan(Math.toRadians(22.5)).toFloat()), scale, 1e-3f)
    }

    @Test
    fun Select_CoarsensWithDistance_AndNeverSkipsALevelThatFails() {
        val errors = floatArrayOf(0f, 0.05f, 0.15f)
        assertEquals(0, ScreenErrorLod.select(errors, 1f, scale, 2f))
        assertEquals(2, ScreenErrorLod.select(errors, 500f, scale, 2f))
        val levels = (1..200).map { ScreenErrorLod.select(errors, it.toFloat(), scale, 2f) }
        assertTrue(levels.zipWithNext().all { (a, b) -> b >= a }, "monotonic: $levels")
        // Level 2 passing but level 1 failing never picks level 2
        assertEquals(0, ScreenErrorLod.select(floatArrayOf(0f, 10f, 0f), 50f, scale, 2f))
    }

    @Test
    fun WaterLod_FlatWaterCoarsensSoonerThanSwell() {
        val flat = WaterLod.errors(0.5f, 0f)
        val swell = WaterLod.errors(0.5f, 1f)
        assertEquals(0f, flat[0])
        assertTrue(flat[2] < swell[2])
        val d = 40f
        assertTrue(WaterLod.select(flat, d, scale) >= WaterLod.select(swell, d, scale))
    }
}
