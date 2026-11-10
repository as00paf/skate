package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.edit.EditTestData.assertSameRotation
import com.pafoid.skate.engine.animation.edit.EditTestData.rotTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.vecTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.yaw
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import org.joml.Quaternionf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TangentsTest {
    private val cubic = InterpolationType.CUBIC_SPLINE

    /** 0 -> 2 -> 0 -> 3 at t = 0, 1, 2, 4. */
    private fun track(mode: TangentMode = TangentMode.AUTO) = vecTrack(
        "Hips", 0f to floatArrayOf(0f, 0f, 0f), 1f to floatArrayOf(2f, 0f, 0f), 2f to floatArrayOf(0f, 0f, 0f), 4f to floatArrayOf(3f, 0f, 0f),
        interpolation = cubic,
    ).let { t -> t.copy(keys = t.keys.map { it.copy(tangentMode = mode) }) }

    @Test
    fun `Tangents_AutoIsCatmullRomFlattenedAtExtremaAndOneSidedAtEnds`() {
        val tangents = Tangents.resolve(track())
        assertEquals(2f, tangents[0].outTangent[0], 1e-6f, "first key: slope to the next key")
        assertEquals(0f, tangents[1].outTangent[0], 1e-6f, "peak: flat, no overshoot")
        assertEquals(0f, tangents[2].inTangent[0], 1e-6f, "valley: flat")
        assertEquals(1.5f, tangents[3].inTangent[0], 1e-6f, "last key: slope from the previous key")

        val rising = Tangents.resolve(vecTrack("Hips", 0f to floatArrayOf(0f, 0f, 0f), 1f to floatArrayOf(1f, 0f, 0f), 3f to floatArrayOf(5f, 0f, 0f), interpolation = cubic))
        assertEquals(5f / 3f, rising[1].outTangent[0], 1e-6f, "non-uniform Catmull-Rom: (p2 - p0) / (t2 - t0)")
    }

    @Test
    fun `Tangents_FlatAndLinearModes`() {
        Tangents.resolve(track(TangentMode.FLAT)).forEach { assertEquals(0f, it.inTangent[0]); assertEquals(0f, it.outTangent[0]) }
        val linear = Tangents.resolve(track(TangentMode.LINEAR))
        assertEquals(2f, linear[1].inTangent[0], 1e-6f)
        assertEquals(-2f, linear[1].outTangent[0], 1e-6f)
        // Linear tangents make the cubic segment a straight line.
        assertEquals(1f, KeyEditing.sample(track(TangentMode.LINEAR), 0.5f)[0], 1e-5f)
    }

    @Test
    fun `Tangents_FlatEasesInAndOut`() {
        val eased = vecTrack("Hips", 0f to floatArrayOf(0f, 0f, 0f), 1f to floatArrayOf(1f, 0f, 0f), interpolation = cubic)
            .let { t -> t.copy(keys = t.keys.map { it.copy(tangentMode = TangentMode.FLAT) }) }
        assertEquals(0.5f, KeyEditing.sample(eased, 0.5f)[0], 1e-5f)
        assertTrue(KeyEditing.sample(eased, 0.1f)[0] < 0.1f, "slow start")
    }

    @Test
    fun `Tangents_FreeUsesStoredHandlesAndSetTangentsCapturesCurrentOnes`() {
        val free = KeyEditing.setTangents(track(), 1, TangentMode.FREE, floatArrayOf(4f, 0f, 0f), floatArrayOf(-4f, 0f, 0f))
        assertEquals(-4f, Tangents.resolve(free)[1].outTangent[0])
        val captured = KeyEditing.setTangents(track(), 0, TangentMode.FREE)
        assertEquals(listOf(2f, 0f, 0f), captured.keys[0].outTangent)
    }

    @Test
    fun `Tangents_RotationKeysAreMadeHemisphereContinuous`() {
        val flipped = Quaternionf(yaw(90f)).let { Quaternionf(-it.x, -it.y, -it.z, -it.w) }
        val track = rotTrack("Spine", 0f to yaw(0f), 1f to flipped, 2f to yaw(180f), interpolation = cubic)
        val values = Tangents.values(track)
        assertTrue(QuatMath.dot(values[0], values[1]) > 0f && QuatMath.dot(values[1], values[2]) > 0f)
        assertSameRotation(QuatMath.toRow(yaw(45f)), KeyEditing.sample(track, 0.5f), 2f, "cubic rotation takes the short way")
    }
}
