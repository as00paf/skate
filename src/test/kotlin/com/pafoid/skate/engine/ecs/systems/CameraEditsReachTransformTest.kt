package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentSettings
import com.pafoid.skate.engine.ecs.components.FirstPersonCameraComponent
import com.pafoid.skate.engine.ecs.components.FollowCameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.gameplay.camera.FirstPersonCameraRig
import com.pafoid.skate.engine.gameplay.camera.FollowCameraRig
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * ECS-21: every inspector edit of a first-person or follow camera reaches the camera Transform (what the Game Viewport
 * renders from) on the next update, in edit mode, in a paused Play and in Play while the camera is easing.
 */
class CameraEditsReachTransformTest {

    enum class Mode { EDIT, PAUSED, PLAY }

    private val dt = 1f / 60f

    private class Rig(mode: Mode, camera: Component) {
        val scene = Scene("cams")
        val skater = GameObject("Skater").addComponent(Transform(Vector3f(2f, 0f, 0f), rotation = Vector3f(0f, 30f, 0f)))
        val other = GameObject("Other").addComponent(Transform(Vector3f(-6f, 1f, 4f), rotation = Vector3f(0f, -90f, 0f)))
        val cam = GameObject("Cam").addComponent(CameraComponent()).addComponent(Transform())
        val cycle = CameraCycleSystem(null, { null }) { _, _ -> }
        val follow = FollowCameraSystem()

        init {
            cam.components.add(camera); camera.init(cam)
            listOf(skater, other, cam).forEach(scene::addChild)
            cycle.init(scene); follow.init(scene)
            update() // frames run before the edit: the first one only records the camera's settings
            if (mode != Mode.EDIT) {
                scene.isRunning = true
                update()
                // Moving target: the camera is mid-ease, so an edit that only eased would not land on the new pose.
                skater.getComponent<Transform>()!!.translation.add(0f, 0f, 2f)
                update()
            }
            if (mode == Mode.PAUSED) scene.isPaused = true
        }

        fun update(dt: Float = 1f / 60f) { cycle.update(dt); follow.update(dt) }

        val transform get() = cam.getComponent<Transform>()!!
    }

    private fun assertPose(expected: Transform, actual: Transform, what: String) {
        assertTrue(expected.translation.equals(actual.translation, 1e-4f), "$what: position ${actual.translation} != ${expected.translation}")
        assertTrue(expected.rotation.equals(actual.rotation, 1e-3f), "$what: rotation ${actual.rotation} != ${expected.rotation}")
    }

    private val firstPersonEdits: Map<String, (FirstPersonCameraComponent) -> Unit> = linkedMapOf(
        "targetName" to { it.targetName = "Other" },
        "eyeHeight" to { it.eyeHeight += 0.7f },
        "forwardOffset" to { it.forwardOffset += 0.6f },
        "pitch" to { it.pitch += 15f },
        "followSharpness" to { it.followSharpness = 0f },
    )

    @ParameterizedTest
    @EnumSource(Mode::class)
    fun FirstPerson_EveryFieldEditReachesTheCameraTransformNextUpdate(mode: Mode) {
        firstPersonEdits.forEach { (field, edit) ->
            val camera = FirstPersonCameraComponent()
            val rig = Rig(mode, camera)

            edit(camera)
            rig.update()

            val expectedCamera = FirstPersonCameraComponent().also(edit)
            val target = rig.scene.findFirstByName(expectedCamera.targetName)!!
            val expected = Transform().also { FirstPersonCameraRig.snap(expectedCamera, FollowCameraSystem.followTarget(target), it) }
            assertPose(expected, rig.transform, "$mode $field")
        }
    }

    private val followEdits: Map<String, (FollowCameraComponent) -> Unit> = linkedMapOf(
        "targetName" to { it.targetName = "Other" },
        "distance" to { it.distance += 2f },
        "pivotHeight" to { it.pivotHeight += 0.8f },
        "pitch" to { it.pitch += 10f },
        "maxPitch" to { it.maxPitch = 5f }, // below pitch: clamps it
        "minPitch" to { it.minPitch = 20f }, // above pitch: clamps it
        "followSharpness" to { it.followSharpness = 0f },
        "presetSharpness" to { it.presetSharpness = 0f },
    )

    @ParameterizedTest
    @EnumSource(Mode::class)
    fun Follow_EveryPoseFieldEditReachesTheCameraTransformNextUpdate(mode: Mode) {
        followEdits.forEach { (field, edit) ->
            val camera = FollowCameraComponent(collision = false)
            val rig = Rig(mode, camera)

            edit(camera)
            rig.update()

            val expectedCamera = FollowCameraComponent(collision = false).also(edit)
            val target = rig.scene.findFirstByName(expectedCamera.targetName)!!
            val expected = Transform().also { FollowCameraRig.snap(expectedCamera, FollowCameraSystem.followTarget(target), null, it) }
            assertPose(expected, rig.transform, "$mode $field")
        }
    }

    @Test
    fun Play_ZeroSharpnessFollowsWithoutLag() {
        val fp = FirstPersonCameraComponent(followSharpness = 0f)
        val rig = Rig(Mode.PLAY, fp)

        rig.skater.getComponent<Transform>()!!.translation.add(3f, 0f, 0f)
        rig.update()

        val target = FollowCameraSystem.followTarget(rig.skater)
        assertTrue(FirstPersonCameraRig.eye(fp, target).equals(rig.transform.translation, 1e-4f))
    }

    @Test
    fun Play_WithoutEditsStillEases() {
        val rig = Rig(Mode.PLAY, FirstPersonCameraComponent())
        val eye = FirstPersonCameraRig.eye(rig.cam.getComponent<FirstPersonCameraComponent>()!!, FollowCameraSystem.followTarget(rig.skater))

        assertNotEquals(eye, rig.transform.translation, "an unedited camera keeps easing after a moving target")
    }

    @Test
    fun Edit_WithoutEditsLeavesThePlacedCameraAlone() {
        val rig = Rig(Mode.EDIT, FollowCameraComponent(collision = false))
        rig.transform.translation.set(7f, 7f, 7f)

        rig.update()

        assertEquals(Vector3f(7f, 7f, 7f), rig.transform.translation)
    }

    @Test
    fun Paused_WithoutEditsHoldsStill() {
        val rig = Rig(Mode.PAUSED, FollowCameraComponent(collision = false))
        val before = Vector3f(rig.transform.translation)

        rig.skater.getComponent<Transform>()!!.translation.add(5f, 0f, 0f)
        rig.update()

        assertEquals(before, rig.transform.translation)
    }

    /** Guard: a field added to either component later is seen as an edit, so it also applies on the next frame. */
    @Test
    fun Settings_EverySavedFieldOfBothComponentsIsWatched() {
        listOf(FirstPersonCameraComponent(), FollowCameraComponent()).forEach { component ->
            component.javaClass.declaredFields
                .filter { !Modifier.isStatic(it.modifiers) && it.name != "state" }
                .forEach { field ->
                    field.isAccessible = true
                    val before = ComponentSettings.of(component, setOf("state"))
                    val old = field.get(component)
                    field.set(component, when (old) {
                        is Float -> old + 1f
                        is Int -> old + 1
                        is Boolean -> !old
                        is String -> old + "x"
                        else -> error("${component.javaClass.simpleName}.${field.name}: add a case for ${old?.javaClass}")
                    })
                    assertNotEquals(before, ComponentSettings.of(component, setOf("state")), "${component.javaClass.simpleName}.${field.name}")
                    field.set(component, old)
                }
        }
    }
}
