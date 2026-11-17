package com.pafoid.skate.engine.physics3d.simple

import com.pafoid.skate.engine.physics3d.ColliderShape
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** PHY-15: SimplePhysics3D's heightfield geometry. */
class HeightGridTest {
    /** 5 × 5 samples 1 m apart, flat at 1 m, corner at (10, 0, 10). */
    private fun flat(rotation: Quaternionf = Quaternionf()) =
        HeightGrid(ColliderShape.Heightfield(5, 1f, FloatArray(25) { 1f })).apply { place(Vector3f(10f, 0f, 10f), rotation) }

    @Test
    fun heightGrid_BoundsCoverTheGrid_AndIgnoreRotation() {
        val grid = flat(Quaternionf().rotationY(1f))
        val min = Vector3f()
        val max = Vector3f()
        grid.bounds(min, max)
        assertEquals(Vector3f(10f, 1f, 10f), min)
        assertEquals(Vector3f(14f, 1f, 14f), max)
        assertEquals(1f, grid.heightAt(12f, 12f))
        assertNull(grid.heightAt(9.9f, 12f))
    }

    @Test
    fun sphereContact_PushesASphereBelowTheSurfaceBackUp() {
        val contact = flat().sphereContact(Vector3f(12f, 0.5f, 12f), 0.25f)
        assertNotNull(contact)
        assertEquals(0f, contact!!.normal.distance(0f, 1f, 0f), 1e-5f)
        assertEquals(0.75f, contact.depth, 1e-5f)
        assertEquals(1f, contact.point.y, 1e-5f)
    }

    @Test
    fun sphereContact_TouchesTheTopFace_AndTheEdgeFromOutside() {
        val grid = flat()
        assertEquals(0.1f, grid.sphereContact(Vector3f(12f, 1.4f, 12f), 0.5f)!!.depth, 1e-5f)
        assertNull(grid.sphereContact(Vector3f(12f, 1.6f, 12f), 0.5f))

        // Beside the west edge, level with the top: pushed away from the edge along -X
        val edge = grid.sphereContact(Vector3f(9.8f, 1f, 12f), 0.5f)
        assertNotNull(edge)
        assertEquals(0f, edge!!.normal.distance(-1f, 0f, 0f), 1e-5f)
        assertEquals(0.3f, edge.depth, 1e-5f)
    }

    @Test
    fun raycast_HitsUpperFacesOnly() {
        val grid = flat()
        val down = grid.raycast(Vector3f(11.3f, 5f, 12.7f), Vector3f(0f, -1f, 0f), 10f, 0f)
        assertEquals(4f, down!!.distance, 1e-5f)
        assertNull(grid.raycast(Vector3f(11.3f, -5f, 12.7f), Vector3f(0f, 1f, 0f), 10f, 0f), "from below")
        assertNull(grid.raycast(Vector3f(11.3f, 5f, 12.7f), Vector3f(0f, -1f, 0f), 3f, 0f), "too short")
    }

    @Test
    fun inflatedRaycast_StopsTheSphereOnTheSurface_AndIgnoresAStartInContact() {
        val grid = flat()
        val hit = grid.raycast(Vector3f(12f, 5f, 12f), Vector3f(0f, -1f, 0f), 10f, 0.5f)
        assertNotNull(hit)
        assertEquals(3.5f, hit!!.distance, 1e-3f)
        assertTrue(hit.normal.y > 0.99f)
        assertNull(grid.raycast(Vector3f(12f, 1.2f, 12f), Vector3f(0f, -1f, 0f), 10f, 0.5f))
    }
}
