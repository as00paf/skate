package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.edit.EditTestData.frames
import com.pafoid.skate.engine.animation.edit.EditTestData.rotTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.scalarTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.yaw
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sin

class ReductionTest {
    private val times = frames(30f, 2f)

    @Test
    fun `Reduction_LinearRampNeedsOnlyItsEndKeys`() {
        val reduced = Reduction.reduce(scalarTrack(times, { it * 3f }), 0.001f)
        assertEquals(listOf(0f, 2f), reduced.keys.map { it.time })
    }

    @Test
    fun `Reduction_ConstantTrackCollapsesToOneKey`() {
        assertEquals(1, Reduction.reduce(scalarTrack(times, { 0.5f }), 0.001f).keys.size)
    }

    @Test
    fun `Reduction_CurvesStayWithinToleranceWithFewerKeys`() {
        for (interp in listOf(InterpolationType.LINEAR, InterpolationType.CUBIC_SPLINE)) {
            val dense = scalarTrack(times, { sin(it * 3f) }, interp)
            val tolerance = 0.02f
            val reduced = Reduction.reduce(dense, tolerance)
            assertTrue(reduced.keys.size < dense.keys.size / 2, "$interp kept ${reduced.keys.size} of ${dense.keys.size}")
            for (k in dense.keys) {
                val err = kotlin.math.abs(KeyEditing.sample(reduced, k.time)[0] - k.value[0])
                assertTrue(err <= tolerance + 1e-5f, "$interp error $err at ${k.time}")
            }
        }
    }

    @Test
    fun `Reduction_RotationToleranceIsInDegrees`() {
        val dense = rotTrack("Hips", *times.map { it to yaw(it * 45f) }.toTypedArray())
        val reduced = Reduction.reduce(dense, 0.1f)
        assertEquals(2, reduced.keys.size, "constant angular speed is a single slerp")

        val clip = SkClip(name = "c", duration = 2f, tracks = listOf(dense, scalarTrack(times, { it })))
        assertTrue(Reduction.reduce(clip).tracks.all { it.keys.size == 2 })
    }
}
