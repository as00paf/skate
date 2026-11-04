package com.pafoid.skate.engine.render.renderer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-19: grid lines on the floor are pulled toward the camera by more than the floor's depth step per pixel. */
class GroundDepthPullTest {
    private val pixelAngle = GroundDepthPull.pixelAngle(Math.toRadians(45.0).toFloat(), 720)

    /** View-depth change of a floor [height] below the camera across one pixel, at view depth [depth] (grazing). */
    private fun floorDepthStepPerPixel(depth: Float, height: Float) = depth * depth * pixelAngle / height

    @Test
    fun GroundDepthPull_CoversTheFloorDepthStepAtTheDefaultEditorView() {
        // Editor camera at (0, 5, 20) looking at a 20 m floor: its far edge is about 30 m away, 5 m below
        for (depth in listOf(5f, 15f, 30f)) {
            val pull = GroundDepthPull.viewDepthPull(depth, 5f, pixelAngle, ground = true)
            assertTrue(pull >= 2f * floorDepthStepPerPixel(depth, 5f), "pull $pull at $depth m")
        }
    }

    @Test
    fun GroundDepthPull_TheOldFixedPullWasBelowOnePixelFarAway() {
        val oldPull = 30f * GroundDepthPull.BASE_PULL
        assertTrue(oldPull < floorDepthStepPerPixel(30f, 5f), "the REN-18 pull could not win the depth test at 30 m")
    }

    @Test
    fun GroundDepthPull_NonGroundLinesKeepTheSmallProportionalPull() {
        assertEquals(10f * GroundDepthPull.BASE_PULL, GroundDepthPull.viewDepthPull(10f, 5f, pixelAngle, ground = false), 1e-6f)
        assertEquals(0f, GroundDepthPull.viewDepthPull(-1f, 5f, pixelAngle, ground = true), 0f, "behind the camera: no pull")
    }

    @Test
    fun GroundDepthPull_IsCappedAtHalfTheDepth() {
        assertEquals(500f, GroundDepthPull.viewDepthPull(1000f, 0f, pixelAngle, ground = true), 1e-3f)
    }

    @Test
    fun DebugLineShader_MirrorsTheKotlinConstants() {
        val src = javaClass.getResourceAsStream("/shaders/debugLine2D.glsl")!!.readBytes().toString(Charsets.UTF_8)
        fun const(name: String) = Regex("""const float $name = ([0-9.]+);""").find(src)!!.groupValues[1].toFloat()
        assertEquals(GroundDepthPull.BASE_PULL, const("BASE_PULL"))
        assertEquals(GroundDepthPull.GROUND_PULL_PIXELS, const("GROUND_PULL_PIXELS"))
    }
}
