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

    @Test
    fun GizmoMath_PickRing_HitsCircleInAxisPlane() {
        val axes = GizmoMath.axes(Vector3f(), false)
        // looking down -z at the z-ring (radius 1): point (1,0) is on the ring
        assertEquals(2, GizmoMath.pickRing(Vector3f(1f, 0f, 10f), Vector3f(0f, 0f, -1f), Vector3f(), axes, 1f, 0.1f).let { if (it == 2) 2 else it })
        assertEquals(-1, GizmoMath.pickRing(Vector3f(0.3f, 0.3f, 10f), Vector3f(0f, 0f, -1f), Vector3f(), axes, 1f, 0.1f))
    }

    @Test
    fun GizmoMath_SignedAngle_QuarterTurnAroundZ() {
        val a = GizmoMath.signedAngle(Vector3f(1f, 0f, 0f), Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f))
        assertEquals(Math.PI.toFloat() / 2f, a, eps)
    }

    @Test
    fun GizmoMath_PickPlane_HitsHandleSquare() {
        val axes = GizmoMath.axes(Vector3f(), false)
        // looking down -z: z-plane handle (index 2) spans x,y in 0.2..0.5 * size
        assertEquals(2, GizmoMath.pickPlane(Vector3f(0.35f, 0.35f, 10f), Vector3f(0f, 0f, -1f), Vector3f(), axes, 1f))
        assertEquals(-1, GizmoMath.pickPlane(Vector3f(0.9f, 0.9f, 10f), Vector3f(0f, 0f, -1f), Vector3f(), axes, 1f))
    }

    @Test
    fun GizmoMath_Pivot_IsCentroid() {
        val p = GizmoMath.pivot(listOf(Vector3f(0f, 0f, 0f), Vector3f(2f, 4f, 6f)))
        assertEquals(1f, p.x, eps); assertEquals(2f, p.y, eps); assertEquals(3f, p.z, eps)
    }

    @Test
    fun GizmoMath_RotateEuler_AroundWorldAxis() {
        val e = GizmoMath.rotateEuler(Vector3f(), Vector3f(0f, 1f, 0f), Math.toRadians(45.0).toFloat())
        assertEquals(45f, e.y, 0.01f)
        assertEquals(0f, e.x, 0.01f)
    }

    @Test
    fun GizmoMath_GizmoSize_OrthographicIgnoresDistance_PerspectiveUsesZoomedFov() {
        assertEquals(
            GizmoMath.gizmoSize(true, 5f, 45f, 2f, 1000),
            GizmoMath.gizmoSize(true, 500f, 45f, 2f, 1000), eps,
        )
        assertEquals(300f, GizmoMath.gizmoSize(true, 1f, 45f, 2f, 1000), eps)
        val zoomed = GizmoMath.gizmoSize(false, 10f, 30f, 2f, 1080)
        assertEquals(GizmoMath.screenSpaceScale(10f, Math.toRadians(60.0).toFloat()), zoomed, eps)
    }

    @Test
    fun GizmoMath_FacingViewer_FlipsAxesPointingAwayFromCamera() {
        val axes = GizmoMath.axes(Vector3f(), false)
        val flipped = GizmoMath.facingViewer(axes, Vector3f(1f, -1f, -1f).normalize())
        assertEquals(-1f, flipped[0].x, eps)
        assertEquals(1f, flipped[1].y, eps)
        assertEquals(1f, flipped[2].z, eps)
        assertEquals(1f, axes[0].x, eps)
    }

    @Test
    fun GizmoMath_ToParentSpace_UndoesParentRotationAndScale() {
        val parent = org.joml.Matrix4f().rotateY(Math.toRadians(90.0).toFloat()).scale(2f)
        val local = GizmoMath.toParentSpace(Vector3f(0f, 0f, -2f), parent)
        assertEquals(1f, local.x, eps)
        assertEquals(0f, local.z, eps)
        assertEquals(Vector3f(1f, 2f, 3f), GizmoMath.toParentSpace(Vector3f(1f, 2f, 3f), null))
    }
}
