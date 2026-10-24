package com.pafoid.skate.editor.gizmos

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GizmoMathTest {
    private val eps = 1e-3f

    @Test
    fun GizmoMath_ScreenSpaceScale_GrowsLinearlyWithDistance() {
        val fov = Math.toRadians(60.0).toFloat()
        assertEquals(2f * GizmoMath.screenSpaceScale(5f, fov), GizmoMath.screenSpaceScale(10f, fov), eps)
        assertTrue(GizmoMath.screenSpaceScale(0f, fov) > 0f)
    }

    @Test
    fun GizmoMath_Axes_WorldIgnoresRotation_LocalFollowsIt() {
        val world = GizmoMath.axes(Vector3f(0f, 90f, 0f), local = false)
        assertEquals(1f, world[0].x, eps)
        val local = GizmoMath.axes(Vector3f(0f, 90f, 0f), local = true)
        assertEquals(0f, local[0].x, eps)
        assertEquals(-1f, local[0].z, eps)
    }

    @Test
    fun GizmoMath_ClosestAxisParam_FindsDragPoint() {
        val t = GizmoMath.closestAxisParam(Vector3f(3f, 0f, 10f), Vector3f(0f, 0f, -1f), Vector3f(), Vector3f(1f, 0f, 0f))
        assertEquals(3f, t!!, eps)
    }

    @Test
    fun GizmoMath_ClosestAxisParam_ParallelRay_ReturnsNull() {
        assertNull(GizmoMath.closestAxisParam(Vector3f(0f, 1f, 0f), Vector3f(1f, 0f, 0f), Vector3f(), Vector3f(1f, 0f, 0f)))
    }

    @Test
    fun GizmoMath_PickAxis_PicksNearestWithinThreshold() {
        val axes = GizmoMath.axes(Vector3f(), false)
        val origin = Vector3f()
        // ray from +z looking at a point on the y axis, offset slightly
        val y = GizmoMath.pickAxis(Vector3f(0.05f, 1f, 10f), Vector3f(0f, 0f, -1f), origin, axes, 2f, 0.2f)
        assertEquals(1, y)
        val miss = GizmoMath.pickAxis(Vector3f(5f, 5f, 10f), Vector3f(0f, 0f, -1f), origin, axes, 2f, 0.2f)
        assertEquals(-1, miss)
    }

    @Test
    fun GizmoMath_PickAxis_WorksFromBehindAndAnyAngle() {
        val axes = GizmoMath.axes(Vector3f(), false)
        val x = GizmoMath.pickAxis(Vector3f(1f, 0f, -10f), Vector3f(0f, 0f, 1f), Vector3f(), axes, 2f, 0.2f)
        assertEquals(0, x)
        val top = GizmoMath.pickAxis(Vector3f(0f, 10f, 1f), Vector3f(0f, -1f, 0f), Vector3f(), axes, 2f, 0.2f)
        assertEquals(2, top)
    }

    @Test
    fun GizmoMath_Snap_RoundsToStep() {
        assertEquals(1.5f, GizmoMath.snap(1.4f, 0.5f), eps)
        assertEquals(1.4f, GizmoMath.snap(1.4f, 0f), eps)
    }
}
