package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.edit.EditTestData.frames
import com.pafoid.skate.engine.animation.edit.EditTestData.rotTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.scalarTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.vecTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.yaw
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

class FiltersTest {
    private val times = frames(60f, 2f)
    private val random = Random(13)
    private val noisy = scalarTrack(times, { it + (random.nextFloat() - 0.5f) * 0.1f })

    /** Mean absolute deviation from the clean ramp `x = t`. */
    private fun noise(track: SkTrack) = track.keys.map { abs(it.value[0] - it.time) }.average()

    @Test
    fun `Filters_GaussianRemovesJitterAndKeepsTimes`() {
        val smooth = Filters.gaussian(noisy, 0.05f)
        assertEquals(noisy.keys.map { it.time }, smooth.keys.map { it.time })
        assertTrue(noise(smooth) < noise(noisy) * 0.5, "noise ${noise(noisy)} -> ${noise(smooth)}")
        val flat = scalarTrack(times, { 2f })
        assertTrue(Filters.gaussian(flat, 0.1f).keys.all { abs(it.value[0] - 2f) < 1e-5f })
    }

    @Test
    fun `Filters_OneEuroRemovesJitter`() {
        // A held pose with sensor jitter (mocap): the filter should settle near the true value.
        val jittery = scalarTrack(times, { 0.5f + (random.nextFloat() - 0.5f) * 0.1f })
        fun jitter(t: SkTrack) = t.keys.drop(10).map { abs(it.value[0] - 0.5f) }.average()
        val smooth = Filters.oneEuro(jittery, minCutoffHz = 1f, beta = 0.5f)
        assertTrue(jitter(smooth) < jitter(jittery) * 0.5, "jitter ${jitter(jittery)} -> ${jitter(smooth)}")
        assertEquals(jittery.keys.first().value, smooth.keys.first().value, "the first sample passes through")
    }

    @Test
    fun `Filters_RotationsStayUnitQuaternions`() {
        val jitter = rotTrack("Hips", *times.map { it to yaw(it * 30f + (random.nextFloat() - 0.5f) * 4f) }.toTypedArray())
        for (t in listOf(Filters.gaussian(jitter, 0.05f), Filters.oneEuro(jitter))) {
            t.keys.forEach { k -> val v = k.value.toFloatArray(); assertEquals(1f, kotlin.math.sqrt(QuatMath.dot(v, v)), 1e-4f) }
        }
    }

    @Test
    fun `Filters_ResampleToFps`() {
        val sparse = vecTrack("Hips", 0f to floatArrayOf(0f, 0f, 0f), 1f to floatArrayOf(10f, 0f, 0f))
        val dense = Filters.resample(sparse, 10f, 1f)
        assertEquals(11, dense.keys.size)
        assertEquals(1f, dense.keys.last().time, 1e-6f)
        assertEquals(3f, dense.keys[3].value[0], 1e-4f)

        val clip = Filters.resample(SkClip(name = "c", fps = 30f, duration = 1f, tracks = listOf(sparse)), 24f)
        assertEquals(24f, clip.fps)
        assertEquals(25, clip.tracks.single().keys.size)
    }
}
