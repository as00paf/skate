package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * PHY-12: every scene body is registered in the physics world exactly once, whatever path loaded the scene.
 * Each editor path reduces to a sequence of [SystemManager.loadScene] calls on scene instances:
 * - new project: the generated in-memory scenes are opened, then the saved files are opened again (fresh copies);
 * - open: one load; scene switch: alternating between two open scenes;
 * - play/stop: the play snapshot restore opens a fresh deserialized copy of the scene.
 */
class PhysicsBodyRegistrationTest {

    private lateinit var physics: PhysicsSystem
    private lateinit var manager: SystemManager

    @BeforeEach
    fun setUp() {
        NativeLibraryLoader().loadNativeLibrary()
        physics = PhysicsSystem(mockk<DebugRenderer>(relaxed = true))
        manager = SystemManager()
        manager.addSystem(physics)
        manager.start()
    }

    /** Builds an equivalent of the default skate scene: floor + skater-like body. Calling it twice mimics a reload. */
    private fun skateScene(name: String = "MainScene"): Scene = Scene().apply {
        this.name = name
        addComponent(ScenePhysicsComponent())
        addChild(GameObject("Floor").apply {
            addComponent(Transform().apply { translation.set(0f, -0.5f, 0f) })
            addComponent(RigidBody3D(0f).apply { bodyType = BodyType.Static })
            addComponent(BoxCollider3D(Vector3f(50f, 0.5f, 50f)))
        })
        addChild(GameObject("Skater").apply {
            addComponent(Transform().apply { translation.set(0f, 1f, 0f) })
            addComponent(RigidBody3D(70f))
            addComponent(BoxCollider3D(Vector3f(0.3f, 0.9f, 0.3f)))
        })
    }

    private fun otherScene(): Scene = Scene().apply {
        name = "OpenGLTest"
        addChild(GameObject("Crate").apply {
            addComponent(Transform())
            addComponent(RigidBody3D(1f))
            addComponent(BoxCollider3D(Vector3f(0.5f)))
        })
    }

    private fun load(scene: Scene) {
        manager.loadScene(scene)
    }

    @Test
    fun openScene_RegistersEachBodyOnce() {
        load(skateScene())

        assertEquals(2, physics.worldBodyCount)
    }

    @Test
    fun sameSceneLoadedTwice_DoesNotDuplicate() {
        // SceneManager.openScenes loads the first scene, then switchScene loads it again
        val scene = skateScene()
        load(scene)
        load(scene)

        assertEquals(2, physics.worldBodyCount)
    }

    @Test
    fun newProject_GeneratedScenesThenReloadedFromDisk_RegistersEachBodyOnce() {
        // PrefabsGenerator.createDefaultScenes opens the in-memory scenes...
        val generated = skateScene()
        load(generated)
        load(generated)
        // ...then ProjectManager.loadDefaultScenes opens the saved files: new instances at the same positions
        val reloaded = skateScene()
        load(reloaded)

        assertEquals(2, physics.worldBodyCount)
        assertNull(generated.children.first().getComponent(RigidBody3D::class.java)?.rawBody)
        assertNotNull(reloaded.children.first().getComponent(RigidBody3D::class.java)?.rawBody)
    }

    @Test
    fun sceneSwitch_KeepsOnlyActiveSceneBodies() {
        val main = skateScene()
        val other = otherScene()

        load(main)
        load(other)
        assertEquals(1, physics.worldBodyCount)

        load(main)
        assertEquals(2, physics.worldBodyCount)
    }

    @Test
    fun playStop_SnapshotRestoreWithFreshCopy_RegistersEachBodyOnce() {
        val edited = skateScene()
        load(edited)
        edited.start()
        repeat(10) { physics.fixedUpdate(1f / 60f) }

        val restored = skateScene() // ScenePlaySnapshot.restore opens a fresh deserialized copy
        load(restored)

        assertEquals(2, physics.worldBodyCount)
    }

    @Test
    fun jointsOnReloadedEqualScene_AreTrackedPerInstanceAndReleased() {
        fun jointScene() = skateScene().apply {
            children.first { it.name == "Skater" }.addComponent(JointComponent())
            start()
        }
        val first = jointScene()
        load(first)
        physics.fixedUpdate(1f / 60f)
        val firstJoint = first.children.first { it.name == "Skater" }.getComponent(JointComponent::class.java)!!
        assertNotNull(firstJoint.constraint)

        val second = jointScene() // equal JointComponent value, different instance
        load(second)
        physics.fixedUpdate(1f / 60f)
        val secondJoint = second.children.first { it.name == "Skater" }.getComponent(JointComponent::class.java)!!
        assertNull(firstJoint.constraint)
        assertNotNull(secondJoint.constraint)
        assertEquals(3, physics.worldBodyCount) // floor + skater + the joint's world anchor

        load(otherScene())
        assertNull(secondJoint.constraint)
        assertEquals(1, physics.worldBodyCount)
    }

    private fun <T : Any> GameObject.getComponent(c: Class<T>): T? = components.filterIsInstance(c).firstOrNull()
}
