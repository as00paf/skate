package com.pafoid.skate.editor.terrain

import com.pafoid.skate.editor.terrain.TerrainStampHandles.Handle
import com.pafoid.skate.engine.terrain.StampFootprint
import com.pafoid.skate.engine.terrain.StampMode
import com.pafoid.skate.engine.terrain.TerrainStamp
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-92 stamp handles and the "Flatten terrain under" footprint. */
class TerrainStampHandlesTest {
    private val rect = TerrainStamp(x = 2f, y = 1f, z = -3f, yaw = 30f, width = 8f, depth = 4f)

    @Test
    fun Outline_MatchesTheEngineFootprint_AtAnyYaw() {
        for (yaw in listOf(0f, 30f, 90f, -135f)) {
            val s = rect.copy(yaw = yaw)
            val corners = TerrainStampHandles.outline(s)
            for (p in corners) assertEquals(0f, s.distanceOutside(p.x, p.z), 1e-3f, "corner on the footprint at yaw $yaw")
            // A point just beyond the +X edge midpoint is outside by that much
            val mid = Vector3f(TerrainStampHandles.axisX(yaw)).mul(s.width * 0.5f + 0.5f).add(s.x, 0f, s.z)
            assertEquals(0.5f, s.distanceOutside(mid.x, mid.z), 1e-3f, "+X edge at yaw $yaw")
            val midZ = Vector3f(TerrainStampHandles.axisZ(yaw)).mul(s.depth * 0.5f + 0.25f).add(s.x, 0f, s.z)
            assertEquals(0.25f, s.distanceOutside(midZ.x, midZ.z), 1e-3f, "+Z edge at yaw $yaw")
        }
        val circle = TerrainStamp(footprint = StampFootprint.CIRCLE, x = 1f, z = 1f, radius = 3f)
        TerrainStampHandles.outline(circle).forEach { assertEquals(0f, circle.distanceOutside(it.x, it.z), 1e-3f) }
    }

    @Test
    fun Handles_PickTheNearest_WithinTolerance() {
        val handles = TerrainStampHandles.handles(rect).toMap()
        assertEquals(setOf(Handle.CENTER, Handle.WIDTH, Handle.DEPTH, Handle.YAW), handles.keys)
        assertEquals(Handle.WIDTH, TerrainStampHandles.pick(rect, Vector3f(handles.getValue(Handle.WIDTH)).add(0.1f, 0f, 0f), 0.5f))
        assertEquals(Handle.CENTER, TerrainStampHandles.pick(rect, Vector3f(2.2f, 0f, -3f), 0.5f))
        assertNull(TerrainStampHandles.pick(rect, Vector3f(50f, 0f, 50f), 0.5f))
        val circle = TerrainStamp(footprint = StampFootprint.CIRCLE, radius = 4f)
        assertTrue(Handle.RADIUS in TerrainStampHandles.handles(circle).toMap())
    }

    @Test
    fun Drag_MovesResizesAndRotates() {
        val moved = TerrainStampHandles.drag(rect, Handle.CENTER, Vector3f(10f, 99f, 4f))
        assertEquals(10f, moved.x); assertEquals(4f, moved.z); assertEquals(rect.y, moved.y, "height kept")

        // Width: twice the distance along the stamp's X axis
        val alongX = Vector3f(TerrainStampHandles.axisX(rect.yaw)).mul(6f).add(rect.x, 0f, rect.z)
        assertEquals(12f, TerrainStampHandles.drag(rect, Handle.WIDTH, alongX).width, 1e-4f)
        val alongZ = Vector3f(TerrainStampHandles.axisZ(rect.yaw)).mul(5f).add(rect.x, 0f, rect.z)
        assertEquals(10f, TerrainStampHandles.drag(rect, Handle.DEPTH, alongZ).depth, 1e-4f)
        assertEquals(TerrainStampHandles.MIN_SIZE, TerrainStampHandles.drag(rect, Handle.WIDTH, Vector3f(rect.x, 0f, rect.z)).width)

        // Yaw: the knob follows the cursor around the centre
        for (yaw in listOf(0f, 45f, 120f, -60f)) {
            val p = Vector3f(TerrainStampHandles.axisZ(yaw)).mul(7f).add(rect.x, 0f, rect.z)
            assertEquals(yaw, TerrainStampHandles.drag(rect, Handle.YAW, p).yaw, 1e-3f)
        }
        val circle = TerrainStamp(footprint = StampFootprint.CIRCLE)
        assertEquals(5f, TerrainStampHandles.drag(circle, Handle.RADIUS, Vector3f(3f, 0f, 4f)).radius, 1e-4f)
    }

    @Test
    fun FlattenUnder_CoversTheBoundsInTheTerrainFrame() {
        val terrainAt = Vector3f(100f, 5f, -50f)
        val s = TerrainStampHandles.flattenUnder(listOf(Vector3f(102f, 7f, -48f) to 1f, Vector3f(106f, 8f, -48f) to 2f), terrainAt)!!
        assertEquals(StampMode.FLATTEN, s.mode); assertEquals(StampFootprint.RECT, s.footprint)
        // X 101..108, Z -50..-46, lowest point 6, 1 m margin, terrain-local
        assertEquals(4.5f, s.x, 1e-4f); assertEquals(2f, s.z, 1e-4f); assertEquals(1f, s.y, 1e-4f)
        assertEquals(9f, s.width, 1e-4f); assertEquals(6f, s.depth, 1e-4f)
        assertEquals(4f, s.falloff)
        assertNull(TerrainStampHandles.flattenUnder(emptyList(), terrainAt))
    }
}
