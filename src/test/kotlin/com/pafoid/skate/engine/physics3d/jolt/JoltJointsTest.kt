package com.pafoid.skate.engine.physics3d.jolt

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.PI

class JoltJointsTest {
    private val pi = PI.toFloat()

    @Test
    fun JoltJoints_SixDofAxisMode_FollowsBulletRule() {
        assertEquals(JoltJoints.AxisMode.FIXED, JoltJoints.sixDofAxisMode(0f, 0f))
        assertEquals(JoltJoints.AxisMode.FREE, JoltJoints.sixDofAxisMode(1f, -1f))
        assertEquals(JoltJoints.AxisMode.LIMITED, JoltJoints.sixDofAxisMode(-1f, 0f))
    }

    @Test
    fun JoltJoints_HingeLimits_ClampToJoltRangeAndFreeWhenInverted() {
        assertEquals(-1f to 1f, JoltJoints.hingeLimits(-1f, 1f))
        assertEquals(-pi to pi, JoltJoints.hingeLimits(-10f, 10f))
        assertEquals(0f to 2f, JoltJoints.hingeLimits(0.5f, 2f))
        assertEquals(-pi to pi, JoltJoints.hingeLimits(1f, -1f))
    }

    @Test
    fun JoltJoints_SliderLimits_AreNegatedAndSwapped() {
        // A may drop 2 m and rise 1 m: Jolt measures B relative to A
        assertEquals(-1f to 2f, JoltJoints.sliderLimits(-2f, 1f))
        assertEquals(-0f to 1f, JoltJoints.sliderLimits(-1f, 0f))
        val free = JoltJoints.sliderLimits(1f, -1f)
        assertEquals(-Float.MAX_VALUE, free.first)
        assertEquals(Float.MAX_VALUE, free.second)
    }

    @Test
    fun JoltJoints_PlaneSpace_IsAUnitPerpendicular() {
        for (n in listOf(Vector3f(1f, 0f, 0f), Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f), Vector3f(1f, 2f, 3f).normalize())) {
            val p = JoltJoints.planeSpace1(n)
            assertEquals(0f, p.dot(n), 1e-5f)
            assertEquals(1f, p.length(), 1e-5f)
        }
    }

    @Test
    fun JoltJoints_HingeNormals_ArePerpendicularToTheirAxes() {
        val axisA = Vector3f(0f, 0f, 1f)
        val axisB = Vector3f(1f, 0f, 0f)
        val (normalA, normalB) = JoltJoints.hingeNormals(axisA, axisB)
        assertEquals(0f, normalA.dot(axisA), 1e-5f)
        assertEquals(0f, normalB.dot(axisB), 1e-5f)
    }
}
