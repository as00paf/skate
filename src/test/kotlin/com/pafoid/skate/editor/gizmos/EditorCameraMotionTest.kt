package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.engine.core.Engine
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class EditorCameraMotionTest {

    private val fpsCases = listOf(30, 60, 144)

    private fun camera() = EditorCamera(mockk<Engine>(relaxed = true), EditorInputState()).also {
        it.transform.translation.set(0f, 0f, 0f)
    }

    /** Runs [seconds] of frames at [fps] through [frame]. */
    private fun simulate(fps: Int, seconds: Float, frame: (Float) -> Unit) {
        val dt = 1f / fps
        repeat(Math.round(seconds * fps)) { frame(dt) }
    }

    @Test
    fun flyDistancePerSecondIsTheSameAt30_60_144Fps() {
        val distances = fpsCases.map { fps ->
            val cam = camera()
            simulate(fps, 1f) { dt -> cam.flyStep(0f, 1f, 0f, dt) }
            cam.transform.translation.length()
        }
        distances.forEach { assertEquals(EditorCameraMotion.FLY_SPEED, it, 1e-3f) }
    }

    @Test
    fun strafeAndVerticalAlsoScaleWithDt() {
        fpsCases.forEach { fps ->
            val cam = camera()
            simulate(fps, 0.5f) { dt -> cam.flyStep(1f, 0f, -1f, dt) }
            val t = cam.transform.translation
            assertEquals(EditorCameraMotion.FLY_SPEED * 0.5f, t.x, 1e-3f)
            assertEquals(-EditorCameraMotion.FLY_SPEED * 0.5f, t.y, 1e-3f)
        }
    }

    @Test
    fun hitchIsClampedAndBadFrameTimesMoveNothing() {
        val cam = camera()
        cam.flyStep(0f, 1f, 0f, 2.5f) // 2.5 s stall
        assertEquals(EditorCameraMotion.FLY_SPEED * EditorCameraMotion.MAX_DT, cam.transform.translation.length(), 1e-4f)
        assertEquals(0f, EditorCameraMotion.clampDt(-0.016f))
        assertEquals(0f, EditorCameraMotion.clampDt(Float.NaN))
        assertEquals(0f, EditorCameraMotion.step(6f, Float.POSITIVE_INFINITY))
    }

    @Test
    fun smoothingComposesAcrossFrameSplits() {
        val whole = EditorCameraMotion.smoothing(8f, 1f / 30)
        val half = EditorCameraMotion.smoothing(8f, 1f / 60)
        // Remaining after one 1/30 frame == remaining after two 1/60 frames.
        assertEquals(1f - whole, (1f - half) * (1f - half), 1e-6f)
    }

    @Test
    fun resetTransitionProgressIsTheSameAt30_60_144Fps() {
        val positions = fpsCases.map { fps ->
            val cam = camera()
            cam.transform.translation.set(10f, 4f, -6f)
            simulate(fps, 0.5f) { dt -> cam.resetStep(dt) }
            Vector3f(cam.transform.translation)
        }
        positions.forEach { p -> assertTrue(p.distance(positions[0]) < 1e-3f, "positions diverge: $positions") }
    }

    @Test
    fun resetArrivesAtTargetIncludingDepthAndZoom() {
        val arrivalSeconds = fpsCases.map { fps ->
            val cam = camera()
            cam.transform.translation.set(0.05f, 0.05f, 3f) // close in x/y, far in z
            cam.transform.rotation.set(30f, 45f, 0f)
            cam.camera.zoom = 3f
            var arrived = false
            var frames = 0
            while (!arrived && frames < fps * 5) { arrived = cam.resetStep(1f / fps); frames++ }
            assertTrue(arrived, "did not arrive at $fps fps")
            assertEquals(Vector3f(0f, 0f, 20f), cam.transform.translation)
            assertEquals(Vector3f(0f, 0f, 0f), cam.transform.rotation)
            assertEquals(1f, cam.camera.zoom)
            frames.toFloat() / fps
        }
        // Arrival time is FPS independent, within two 30 FPS frames.
        arrivalSeconds.forEach { assertTrue(abs(it - arrivalSeconds[0]) <= 2f / 30, "arrival times: $arrivalSeconds") }
    }
}
