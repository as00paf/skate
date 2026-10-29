package com.pafoid.skate.editor.gizmos

import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditorVisualsTest {
    @Test
    fun EditorVisuals_FrustumHasTwelveEdgesAndFarPlaneAtLength() {
        val segs = EditorVisuals.cameraFrustum(Vector3f(), Quaternionf(), false, 90f, 1f, 0f, 0.1f, 100f, 5f)
        assertEquals(12, segs.size)
        assertEquals(-5f, segs.maxOf { minOf(it.a.z, it.b.z) }.let { segs.minOf { s -> minOf(s.a.z, s.b.z) } }, 1e-4f)
    }

    @Test
    fun EditorVisuals_PointLightRangeShrinksWithQuadratic() {
        val near = EditorVisuals.pointLightRange(1f, 0.09f, 0.032f)
        val far = EditorVisuals.pointLightRange(1f, 0.09f, 0.001f)
        assertTrue(near < far)
        assertEquals(EditorVisuals.DEFAULT_RANGE, EditorVisuals.pointLightRange(1f, 0f, 0f))
    }

    @Test
    fun EditorVisuals_PickReturnsNearestHitAndIgnoresMisses() {
        val targets = listOf(
            EditorVisuals.Target("far", Vector3f(0f, 0f, -10f)),
            EditorVisuals.Target("near", Vector3f(0.1f, 0f, -5f)),
            EditorVisuals.Target("off", Vector3f(5f, 0f, -3f)),
        )
        assertEquals("near", EditorVisuals.pick(Vector3f(), Vector3f(0f, 0f, -1f), targets, 0.5f))
        assertNull(EditorVisuals.pick(Vector3f(), Vector3f(0f, 1f, 0f), targets, 0.5f))
    }

    @Test
    fun EditorVisuals_SpotConeEndsInCircleAtRange() {
        val segs = EditorVisuals.spotCone(Vector3f(), Vector3f(0f, 0f, -1f), 45f, 4f)
        assertTrue(segs.any { kotlin.math.abs(it.b.z + 4f) < 1e-3f })
    }
}
