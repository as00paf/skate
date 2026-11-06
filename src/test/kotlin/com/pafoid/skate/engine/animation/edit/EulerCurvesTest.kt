package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.edit.EditTestData.assertSameRotation
import com.pafoid.skate.engine.animation.edit.EditTestData.rotTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.yaw
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class EulerCurvesTest {
    @Test
    fun `EulerCurves_QuatToEulerToQuatIsTheSameRotation`() {
        val q = EulerCurves.toQuat(Vector3f(30f, -20f, 75f))
        val e = EulerCurves.toEulerDeg(q)
        assertEquals(30f, e.x, 1e-3f); assertEquals(-20f, e.y, 1e-3f); assertEquals(75f, e.z, 1e-3f)
        val samples = listOf(Vector3f(10f, 80f, -170f), Vector3f(-120f, 45f, 200f), Vector3f(0f, 0f, 0f))
        for (deg in samples) {
            val again = EulerCurves.toQuat(EulerCurves.toEulerDeg(EulerCurves.toQuat(deg)))
            assertSameRotation(QuatMath.toRow(EulerCurves.toQuat(deg)), QuatMath.toRow(again), 0.01f, "$deg")
        }
    }

    @Test
    fun `EulerCurves_UnwrapsSoCurvesStayContinuous`() {
        val track = rotTrack("Hips", 0f to yaw(150f), 1f to yaw(175f), 2f to yaw(200f), 3f to yaw(250f), 4f to yaw(400f))
        val curves = EulerCurves.fromTrack(track)
        for (i in 1 until curves.size) {
            val step = abs(curves.at(i).distance(curves.at(i - 1)))
            assertTrue(step < 160f, "jump of $step deg between keys ${i - 1} and $i")
        }
        val expectedYaw = floatArrayOf(150f, 175f, 200f, 250f, 400f)
        for (i in 0 until curves.size) {
            assertEquals(expectedYaw[i], curves.y[i], 0.05f, "yaw key $i")
            assertEquals(0f, curves.x[i], 0.05f, "pitch key $i")
            assertEquals(0f, curves.z[i], 0.05f, "roll key $i")
        }
        for (i in 0 until curves.size) {
            assertSameRotation(track.keys[i].value.toFloatArray(), QuatMath.toRow(EulerCurves.toQuat(curves.at(i))), 0.05f, "key $i")
        }
    }

    @Test
    fun `EulerCurves_EditedCurveWritesBackQuaternionKeys`() {
        val track = rotTrack("Hips", 0f to Quaternionf(), 1f to Quaternionf())
        val edited = EulerCurves.fromTrack(track).withKey(1, Vector3f(0f, 0f, 90f)).toTrack(track)
        assertSameRotation(QuatMath.toRow(Quaternionf().rotationZ(Math.toRadians(90.0).toFloat())), edited.keys[1].value.toFloatArray())
        assertSameRotation(QuatMath.toRow(Quaternionf().rotationZ(Math.toRadians(45.0).toFloat())), KeyEditing.sample(edited, 0.5f), 0.05f)
        assertEquals(listOf(0f, 1f), edited.keys.map { it.time })
    }
}
