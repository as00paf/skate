package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.edit.EditTestData.assertSameRotation
import com.pafoid.skate.engine.animation.edit.EditTestData.rotTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.vecTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.yaw
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** ANI-15: the curve editor's pure core (curve view, value drag, tangent modes, handles). */
class CurveEditingTest {
    private val ramp = vecTrack("Hips", 0f to floatArrayOf(0f, 0f, 0f), 1f to floatArrayOf(1f, 2f, 0f), 2f to floatArrayOf(0f, 0f, 0f))

    @Test
    fun `CurveView_KeysSitOnTheSampledCurve`() {
        val view = CurveEditing.view(ramp, 0f, 2f, 21)
        assertEquals(listOf(0f, 1f, 0f), view.keyValues[0].toList())
        assertEquals(listOf(0f, 2f, 0f), view.keyValues[1].toList())
        val i = view.sampleTimes.indexOfFirst { abs(it - 0.5f) < 1e-5f }
        assertEquals(0.5f, view.sampleValues[0][i], 1e-5f, "linear halfway")
        assertEquals(0f to 2f, view.range())
        assertEquals(0f to 1f, view.range(setOf(0)))
    }

    @Test
    fun `CurveView_RotationsAreContinuousEulerDegrees`() {
        // 170 -> 190 degrees of yaw crosses the ±180 wrap; the curve must not jump by 360.
        val track = rotTrack("Hips", 0f to yaw(170f), 1f to yaw(190f))
        val view = CurveEditing.view(track, 0f, 1f, 11)
        val ys = view.sampleValues[1]
        for (k in 1 until ys.size) assertTrue(abs(ys[k] - ys[k - 1]) < 5f, "jump at sample $k: ${ys[k - 1]} -> ${ys[k]}")
        assertEquals(20f, abs(view.keyValues[1][1] - view.keyValues[1][0]), 0.05f)
    }

    @Test
    fun `OffsetKeys_MovesOneComponentOfTheSelectedKeys`() {
        val moved = CurveEditing.offsetKeys(ramp, setOf(1), 1, 0.5f)
        assertEquals(listOf(1f, 2.5f, 0f), moved.keys[1].value)
        assertEquals(ramp.keys[0], moved.keys[0])
    }

    @Test
    fun `OffsetKeys_RotationEditsEulerDegreesAndStaysAQuaternion`() {
        val track = rotTrack("Hips", 0f to yaw(0f), 1f to yaw(30f))
        val moved = CurveEditing.offsetKeys(track, setOf(1), 1, 15f)
        assertSameRotation(QuatMath.toRow(yaw(45f)), moved.keys[1].value.toFloatArray())
        assertSameRotation(QuatMath.toRow(yaw(0f)), moved.keys[0].value.toFloatArray())
        SkAnimFormat.validate(SkClip(name = "c", duration = 1f, tracks = listOf(moved)))
    }

    @Test
    fun `SetTangent_FlatOnALinearTrackKeepsTheOtherSegmentsLinear`() {
        val flat = CurveEditing.setTangent(ramp, setOf(1), CurveTangent.FLAT)
        assertEquals(InterpolationType.CUBIC_SPLINE, flat.interpolation)
        assertEquals(TangentMode.FLAT, flat.keys[1].tangentMode)
        assertEquals(TangentMode.LINEAR, flat.keys[0].tangentMode)
        // The key value holds and the curve eases into it: flatter than the straight line just before the key.
        assertEquals(1f, KeyEditing.sample(flat, 1f)[0], 1e-5f)
        assertTrue(KeyEditing.sample(flat, 0.9f)[0] > KeyEditing.sample(ramp, 0.9f)[0])
        assertEquals(CurveTangent.FLAT, CurveEditing.currentTangent(flat, setOf(1)))
        assertNull(CurveEditing.currentTangent(flat, setOf(0, 1)), "mixed modes")
    }

    @Test
    fun `SetTangent_LinearOnALinearTrackDoesNotChangeTheMotion`() {
        val cubic = CurveEditing.setTangent(ramp, setOf(0, 1, 2), CurveTangent.LINEAR)
        for (t in listOf(0.25f, 0.5f, 1.3f, 1.9f)) EditTestData.assertVec(KeyEditing.sample(ramp, t), KeyEditing.sample(cubic, t), message = "t=$t")
    }

    @Test
    fun `SetTangent_StepHoldsEachKey`() {
        val step = CurveEditing.setTangent(ramp, setOf(1), CurveTangent.STEP)
        assertEquals(InterpolationType.STEP, step.interpolation)
        assertEquals(0f, KeyEditing.sample(step, 0.9f)[0], 1e-6f)
        assertEquals(CurveTangent.STEP, CurveEditing.currentTangent(step, setOf(0)))
    }

    @Test
    fun `SetHandle_FreeMovesBothSidesAndBrokenOnlyOne`() {
        val free = CurveEditing.setHandle(ramp, 1, 0, HandleSide.OUT, 3f)
        assertEquals(TangentMode.FREE, free.keys[1].tangentMode)
        assertEquals(3f, free.keys[1].inTangent!![0]); assertEquals(3f, free.keys[1].outTangent!![0])
        assertEquals(3f, CurveEditing.handles(free, 1)!!.outTangent[0])

        val broken = CurveEditing.setTangent(free, setOf(1), CurveTangent.BROKEN)
        val corner = CurveEditing.setHandle(broken, 1, 0, HandleSide.OUT, -2f)
        assertEquals(TangentMode.BROKEN, corner.keys[1].tangentMode)
        assertEquals(3f, corner.keys[1].inTangent!![0], "in handle kept")
        assertEquals(-2f, corner.keys[1].outTangent!![0])
        // A stored slope shows up in the played curve: just after the key the value falls.
        assertTrue(KeyEditing.sample(corner, 1.05f)[0] < 1f)
        SkAnimFormat.decode(SkAnimFormat.encode(SkClip(name = "c", duration = 2f, tracks = listOf(corner)))).also {
            assertEquals(TangentMode.BROKEN, it.tracks[0].keys[1].tangentMode, "BROKEN round-trips through .skanim")
        }
    }

    @Test
    fun `SetHandle_RejectsRotations`() {
        assertThrows(IllegalArgumentException::class.java) {
            CurveEditing.setHandle(rotTrack("Hips", 0f to yaw(0f), 1f to yaw(10f)), 0, 1, HandleSide.IN, 1f)
        }
        assertNull(CurveEditing.handles(ramp, 1), "linear tracks have no handles")
    }

    @Test
    fun `DisplayValue_ShowsRotationsInDegrees`() {
        assertEquals(30f, CurveEditing.displayValue(AnimationPath.ROTATION, QuatMath.toRow(yaw(30f)).toList()).y, 1e-3f)
        assertEquals(2f, CurveEditing.displayValue(AnimationPath.TRANSLATION, listOf(1f, 2f, 3f)).y)
    }
}
