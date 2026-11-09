package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.CameraCycleComponent
import com.pafoid.skate.engine.ecs.components.FirstPersonCameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.gameplay.camera.CameraCycle
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.input.InputReader
import com.pafoid.skate.engine.ui.UIButtonComponent
import io.mockk.every
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-22: the camera cycle (action and HUD button) and the first-person camera, without GL or a window. */
class CameraCycleSystemTest {

    private val dt = 1f / 60f
    private var pressed = false
    private val reader = mockk<InputReader>().also {
        every { it.wasActionPressed(any()) } answers { pressed && firstArg<String>() == CameraCycle.ACTION }
    }

    private val scene = Scene("test")
    private val third = camera("Camera", isDefault = true)
    private val firstPerson = camera("FirstPersonCamera").addComponent(FirstPersonCameraComponent(targetName = "Skater"))
    private val fixed = camera("FixedCamera")
    private val button = GameObject("CameraButton").addComponent(UIButtonComponent("hud.camera"))
    private var active: CameraComponent? = third.getComponent<CameraComponent>()
    private val switches = ArrayList<String>()
    private val system = CameraCycleSystem(reader, { active }) { camera, transform ->
        val go = listOf(third, firstPerson, fixed).single { it.getComponent<CameraComponent>() === camera }
        assertSame(go.getComponent<Transform>(), transform)
        active = camera
        switches += go.name
    }

    init {
        third.addComponent(CameraCycleComponent("Camera, FirstPersonCamera, FixedCamera", buttonName = "CameraButton"))
        scene.addChild(GameObject("Skater").addComponent(Transform(Vector3f(2f, 0f, 0f))))
        listOf(third, firstPerson, fixed, GameObject("HUD").also { it.addChild(button) }).forEach { scene.addChild(it) }
        system.init(scene)
    }

    private fun camera(name: String, isDefault: Boolean = false) =
        GameObject(name).addComponent(CameraComponent(isDefault = isDefault)).addComponent(Transform())

    private fun press() {
        pressed = true
        system.update(dt)
        pressed = false
        system.update(dt)
    }

    private val activeName get() = listOf(third, firstPerson, fixed).singleOrNull { it.getComponent<CameraComponent>() === active }?.name

    @Test
    fun action_CyclesThirdPersonFirstPersonFixedAndBack() {
        scene.isRunning = true

        repeat(4) { press() }

        assertEquals(listOf("FirstPersonCamera", "FixedCamera", "Camera", "FirstPersonCamera"), switches)
    }

    @Test
    fun hudButton_IsBoundByNameAndCyclesLikeTheAction() {
        scene.isRunning = true
        system.update(dt)
        val onClick = button.getComponent<UIButtonComponent>()!!.onClick
        assertNotNull(onClick)

        onClick!!()
        system.update(dt)
        onClick()
        onClick()
        system.update(dt)

        assertEquals(listOf("FirstPersonCamera", "Camera"), switches)
    }

    @Test
    fun editMode_NeitherActionNorClicksSwitch() {
        system.update(dt)
        button.getComponent<UIButtonComponent>()!!.onClick!!()
        press()

        scene.isRunning = true
        system.update(dt)

        assertEquals(emptyList<String>(), switches)
    }

    @Test
    fun cycleFollowsTheActiveCamera_SoRestartingOnTheDefaultStartsOver() {
        scene.isRunning = true
        press()
        press()
        assertEquals("FixedCamera", activeName)

        // Restart / Stop+Play reselect the default camera outside of the cycle system.
        active = third.getComponent<CameraComponent>()
        press()
        assertEquals("FirstPersonCamera", activeName)

        // A camera that is not in the list (e.g. the editor camera) goes to the first one.
        active = CameraComponent()
        press()
        assertEquals("Camera", activeName)
    }

    @Test
    fun missingCamerasAreSkipped() {
        scene.removeChild(firstPerson)
        scene.isRunning = true

        press()

        assertEquals("FixedCamera", activeName)
    }

    @Test
    fun firstPersonCamera_SitsAtTheTargetsEyesLookingTheWayItFaces() {
        scene.isRunning = true
        press()

        val t = firstPerson.getComponent<Transform>()!!
        val fp = firstPerson.getComponent<FirstPersonCameraComponent>()!!
        // Skater at (2, 0, 0) facing +Z: eyes 1.6 m up, 0.25 m ahead; camera yaw 180 looks along +Z ([Transform.lookAt]).
        assertEquals(Vector3f(2f, fp.eyeHeight, fp.forwardOffset), t.translation)
        assertEquals(180f, t.rotation.y, 1e-3f)
        assertEquals(fp.pitch, t.rotation.x, 0f)
        val look = Transform(Vector3f(t.translation)).apply { lookAt(Vector3f(t.translation).add(0f, 0f, 1f)) }
        assertEquals(look.rotation.y, t.rotation.y, 1e-3f)
    }

    @Test
    fun firstPersonCamera_EasesAfterTheTargetOnceSnapped() {
        scene.isRunning = true
        system.update(dt)
        val skater = scene.findFirstByName("Skater")!!.getComponent<Transform>()!!
        skater.translation.add(0f, 0f, 1f)

        system.update(dt)

        val z = firstPerson.getComponent<Transform>()!!.translation.z
        assertTrue(z > 0.25f && z < 1.25f, "z=$z")
    }

    @Test
    fun editMode_LeavesThePlacedFirstPersonCameraAlone() {
        firstPerson.getComponent<Transform>()!!.translation.set(5f, 5f, 5f)

        system.update(dt)

        assertEquals(Vector3f(5f, 5f, 5f), firstPerson.getComponent<Transform>()!!.translation)
    }

    @Test
    fun freezesWhilePaused() {
        assertFalse(system.runsWhilePaused)
    }
}
