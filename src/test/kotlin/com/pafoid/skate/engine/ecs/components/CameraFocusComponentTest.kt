package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.ui.UIButtonComponent
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CameraFocusComponentTest {

    /** World-space view direction the engine's [CameraManager] produces for a camera transform. */
    private fun viewDirection(transform: Transform): Vector3f {
        val manager = CameraManager(EventSystem())
        manager.camera = CameraComponent()
        manager.transform = transform.also { it.worldMatrix.translation(it.translation) }
        manager.update(0f)
        return manager.camera.inverseView.transformDirection(Vector3f(0f, 0f, -1f)).normalize()
    }

    @Test
    fun `lookingAt gives a rotation the camera manager renders towards the target`() {
        val cases = listOf(
            Vector3f(0f, 22f, 42f) to Vector3f(0f, 0f, -1f),
            Vector3f(-22f, 9f, 0f) to Vector3f(-22f, 1f, -12f),
            Vector3f(5f, 2f, 5f) to Vector3f(-3f, 0f, 1f),
            Vector3f(0f, 3f, -10f) to Vector3f(0f, 1f, 0f),
        )
        cases.forEach { (eye, target) ->
            val pose = CameraFocusTarget.lookingAt("t", eye, target)
            val expected = Vector3f(target).sub(eye).normalize()
            val actual = viewDirection(Transform(Vector3f(pose.position), rotation = Vector3f(pose.rotation)))
            assertTrue(actual.distance(expected) < 1e-4f, "eye $eye -> $target: got $actual, expected $expected")
        }
    }

    @Test
    fun `negative pitch looks up and positive pitch looks down (TLG-09 root cause)`() {
        assertTrue(viewDirection(Transform(Vector3f(), rotation = Vector3f(-28f, 0f, 0f))).y > 0.4f)
        assertTrue(viewDirection(Transform(Vector3f(), rotation = Vector3f(28f, 0f, 0f))).y < -0.4f)
        val down = CameraFocusTarget.lookingAt("t", Vector3f(0f, 10f, 10f), Vector3f())
        assertTrue(down.rotation.x > 0f)
    }

    @Test
    fun `smoothing is frame-rate independent and clamps hitches`() {
        fun run(fps: Int): Vector3f {
            val transform = Transform(Vector3f())
            val target = CameraFocusTarget("a", position = Vector3f(10f, 0f, 0f), rotation = Vector3f(0f, 90f, 0f))
            repeat(fps / 2) { CameraFocusComponent.step(transform, target, CameraFocusComponent.smoothing(4f, 1f / fps)) }
            return transform.translation
        }
        val at30 = run(30); val at60 = run(60); val at144 = run(144)
        assertEquals(at60.x, at30.x, 1e-3f)
        assertEquals(at60.x, at144.x, 1e-3f)
        assertEquals(10f * (1f - kotlin.math.exp(-2f)), at60.x, 1e-3f)
        assertEquals(CameraFocusComponent.smoothing(4f, CameraFocusComponent.MAX_DT), CameraFocusComponent.smoothing(4f, 5f))
        assertEquals(0f, CameraFocusComponent.smoothing(4f, -1f))
    }

    @Test
    fun `yaw takes the short way round`() {
        assertEquals(20f, CameraFocusComponent.deltaDegrees(350f, 10f), 1e-4f)
        assertEquals(-20f, CameraFocusComponent.deltaDegrees(10f, -10f + 360f), 1e-4f)
        val transform = Transform(Vector3f(), rotation = Vector3f(0f, 170f, 0f))
        CameraFocusComponent.step(transform, CameraFocusTarget("a", rotation = Vector3f(0f, -170f, 0f)), 0.5f)
        assertEquals(180f, transform.rotation.y, 1e-3f)
    }

    @Test
    fun `clicking a bound UI button flies the camera to its target and stops there`() {
        val root = GameObject("Scene")
        val button = UIButtonComponent("label")
        root.addChild(GameObject("Canvas").also { it.addChild(GameObject("GoThere").addComponent(button)) })
        val target = CameraFocusTarget("there", "GoThere", Vector3f(4f, 5f, 6f), Vector3f(30f, 45f, 0f))
        val focus = CameraFocusComponent(mutableListOf(target))
        val camera = GameObject("Camera").addComponent(Transform(Vector3f())).addComponent(focus)
        root.addChild(camera)
        val transform = camera.components.filterIsInstance<Transform>().single()

        focus.update(0.016f)
        assertNull(focus.active)
        button.onClick!!.invoke()
        assertSame(target, focus.active)
        repeat(300) { focus.update(1f / 60f) }

        assertNull(focus.active)
        assertEquals(Vector3f(4f, 5f, 6f), transform.translation)
        assertEquals(Vector3f(30f, 45f, 0f), transform.rotation)
        assertFalse(focus.focus("missing"))
    }
}
