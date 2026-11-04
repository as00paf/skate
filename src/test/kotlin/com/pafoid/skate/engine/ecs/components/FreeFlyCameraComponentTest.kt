package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.systems.CameraManager
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FreeFlyCameraComponentTest {

    private fun rendered(rotation: Vector3f): Pair<Vector3f, Vector3f> {
        val manager = CameraManager(EventSystem())
        manager.camera = CameraComponent()
        manager.transform = Transform(Vector3f(), rotation = rotation)
        manager.update(0f)
        val inv = manager.camera.inverseView
        return inv.transformDirection(Vector3f(0f, 0f, -1f)).normalize() to inv.transformDirection(Vector3f(1f, 0f, 0f)).normalize()
    }

    @Test
    fun `forward and right match what the camera manager renders`() {
        listOf(Vector3f(), Vector3f(30f, 0f, 0f), Vector3f(-20f, 90f, 0f), Vector3f(45f, -135f, 0f), Vector3f(10f, 200f, 0f)).forEach { r ->
            val (fwd, right) = rendered(r)
            assertTrue(FreeFlyCameraComponent.forward(r).distance(fwd) < 1e-4f, "forward $r")
            val flatRight = Vector3f(right.x, 0f, right.z).normalize()
            assertTrue(FreeFlyCameraComponent.right(r).distance(flatRight) < 1e-4f, "right $r")
        }
    }

    @Test
    fun `flying is frame-rate independent, Shift is faster and hitches are clamped`() {
        fun distance(fps: Int, fast: Boolean = false): Float {
            val t = Transform(Vector3f(), rotation = Vector3f(20f, 30f, 0f))
            val ff = FreeFlyCameraComponent(speed = 8f, fastMultiplier = 4f)
            repeat(fps) { ff.fly(t, 0f, 1f, 0f, fast, 1f / fps) }
            return t.translation.length()
        }
        assertEquals(8f, distance(30), 1e-3f)
        assertEquals(8f, distance(60), 1e-3f)
        assertEquals(8f, distance(144), 1e-3f)
        assertEquals(32f, distance(60, fast = true), 1e-3f)
        val t = Transform(Vector3f())
        FreeFlyCameraComponent(speed = 8f).fly(t, 1f, 0f, 0f, false, 2f)
        assertEquals(0.8f, t.translation.length(), 1e-4f)
    }

    @Test
    fun `strafe and vertical axes, diagonal not faster`() {
        val ff = FreeFlyCameraComponent(speed = 10f)
        val t = Transform(Vector3f())
        ff.fly(t, 1f, 0f, 0f, false, 0.1f)
        assertTrue(t.translation.distance(Vector3f(1f, 0f, 0f)) < 1e-4f)
        t.translation.set(0f)
        ff.fly(t, 0f, 0f, 1f, false, 0.1f)
        assertTrue(t.translation.distance(Vector3f(0f, 1f, 0f)) < 1e-4f)
        t.translation.set(0f)
        ff.fly(t, 1f, 1f, 0f, false, 0.1f)
        assertEquals(1f, t.translation.length(), 1e-4f)
        assertFalse(ff.fly(t, 0f, 0f, 0f, false, 0.1f))
    }

    @Test
    fun `mouse look turns right and down and clamps pitch`() {
        val ff = FreeFlyCameraComponent(lookSensitivity = 0.1f)
        val t = Transform(Vector3f())
        ff.look(t, 100f, 50f)
        assertEquals(10f, t.rotation.y, 1e-4f)
        assertEquals(5f, t.rotation.x, 1e-4f)
        assertTrue(FreeFlyCameraComponent.forward(t.rotation).y < 0f)
        ff.look(t, 0f, 10_000f)
        assertEquals(FreeFlyCameraComponent.MAX_PITCH, t.rotation.x)
    }
}
