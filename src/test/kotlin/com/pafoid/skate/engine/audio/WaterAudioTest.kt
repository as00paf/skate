package com.pafoid.skate.engine.audio

import com.pafoid.skate.engine.assets.AssetPaths
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.PI

/** AUD-01: the pure parts of the water sounds. */
class WaterAudioTest {

    private fun assertVec(expected: Vector3f, actual: Vector3f) =
        assertTrue(expected.distance(actual) < 1e-4f, "expected $expected, was $actual")

    @Test
    fun SplashGain_GrowsWithDownwardSpeed_AndIsBounded() {
        assertEquals(WaterAudio.MIN_SPLASH_GAIN, WaterAudio.splashGain(Vector3f()))
        // Rising out of the water is not an impact
        assertEquals(WaterAudio.MIN_SPLASH_GAIN, WaterAudio.splashGain(Vector3f(0f, 5f, 0f)))
        val slow = WaterAudio.splashGain(Vector3f(0f, -2f, 0f))
        val fast = WaterAudio.splashGain(Vector3f(0f, -6f, 0f))
        assertTrue(slow > WaterAudio.MIN_SPLASH_GAIN && fast > slow)
        assertEquals(1f, WaterAudio.splashGain(Vector3f(0f, -50f, 0f)))
        // Sideways counts, but half as much as falling
        assertTrue(WaterAudio.splashGain(Vector3f(4f, 0f, 0f)) < WaterAudio.splashGain(Vector3f(0f, -4f, 0f)))
        assertTrue(WaterAudio.splashGain(Vector3f(4f, 0f, 0f)) > WaterAudio.MIN_SPLASH_GAIN)
    }

    @Test
    fun NearestSurfacePoint_OverTheWater_IsStraightBelowTheListener() {
        val p = WaterAudio.nearestSurfacePoint(Vector3f(0f, 1f, 0f), 0f, 10f, 6f, Vector3f(2f, 5f, -1f))
        assertVec(Vector3f(2f, 1f, -1f), p)
    }

    @Test
    fun NearestSurfacePoint_Outside_IsOnTheClosestEdgeOrCorner() {
        val center = Vector3f(10f, 0f, 0f)
        assertVec(Vector3f(15f, 0f, 1f), WaterAudio.nearestSurfacePoint(center, 0f, 10f, 6f, Vector3f(30f, 2f, 1f)))
        assertVec(Vector3f(5f, 0f, -3f), WaterAudio.nearestSurfacePoint(center, 0f, 10f, 6f, Vector3f(-4f, 2f, -20f)))
    }

    @Test
    fun NearestSurfacePoint_FollowsTheWaterYaw() {
        // Turned a quarter: the long side (sizeX = 10) now runs along world Z, as WaterQueries sees it
        val yaw = (PI / 2).toFloat()
        val p = WaterAudio.nearestSurfacePoint(Vector3f(), yaw, 10f, 2f, Vector3f(0f, 0f, 20f))
        assertVec(Vector3f(0f, 0f, 5f), p)
        val side = WaterAudio.nearestSurfacePoint(Vector3f(), yaw, 10f, 2f, Vector3f(20f, 0f, 0f))
        assertVec(Vector3f(1f, 0f, 0f), side)
    }

    @Test
    fun SplashCooldown_IsPerBody() {
        val cooldown = SplashCooldown()
        assertTrue(cooldown.tryStart(1, 0f, 0.5f))
        assertFalse(cooldown.tryStart(1, 0.3f, 0.5f))
        assertTrue(cooldown.tryStart(2, 0.3f, 0.5f))
        assertTrue(cooldown.tryStart(1, 0.6f, 0.5f))
        cooldown.clear()
        assertTrue(cooldown.tryStart(1, 0.61f, 0.5f))
    }

    @Test
    fun DefaultClips_AreBundledEngineSounds() {
        val paths = AssetPaths()
        (WaterAudio.DEFAULT_SPLASHES + WaterAudio.DEFAULT_AMBIENCE).forEach {
            assertTrue(it.startsWith("engine:Sounds/Water/"), it)
            assertTrue(File(paths.localPath(it)).isFile, "missing bundled sound $it")
        }
    }
}
