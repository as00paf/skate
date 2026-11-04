package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.mockk
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** PHY-11: bodies follow Transform changes made outside the simulation. */
class PhysicsTransformSyncTest {

    private val dt = 1f / 60f
    private lateinit var world: BulletPhysics3D
    private lateinit var physics: PhysicsSystem
    private lateinit var scene: Scene
    private lateinit var box: GameObject

    private val transform get() = box.components.filterIsInstance<Transform>().first()
    private val body get() = box.components.filterIsInstance<RigidBody3D>().first()

    @BeforeEach
    fun setUp() {
        NativeLibraryLoader().loadNativeLibrary()
        world = BulletPhysics3D(mockk<DebugRenderer>(relaxed = true))
        physics = PhysicsSystem(mockk(relaxed = true), physics3d = world)
        box = boxObject("Box", Vector3f(0f, 1f, 0f))
        scene = Scene().apply { addChild(box) }
        SystemManager().apply {
            addSystem(physics)
            loadScene(scene)
            start()
        }
    }

    @AfterEach
    fun tearDown() = world.destroy()

    private fun boxObject(name: String, pos: Vector3f) = GameObject(name).apply {
        addComponent(Transform().apply { translation.set(pos) })
        addComponent(RigidBody3D(1f))
        addComponent(BoxCollider3D(Vector3f(0.5f)))
    }

    private fun bodyPosition() = body.rawBody!!.getLocation(Vector3f())

    private fun assertVec(expected: Vector3f, actual: Vector3f, eps: Float = 1e-3f) =
        assertTrue(expected.distance(actual) < eps, "expected $expected but was $actual")

    @Test
    fun editMode_MoveAndRotate_TeleportsBody() {
        transform.translation.set(3f, 4f, -2f)
        transform.rotation.set(0f, 90f, 0f)

        physics.onFrame(0f, 1f)

        assertVec(Vector3f(3f, 4f, -2f), bodyPosition())
        val expected = Quaternionf().rotationY(Math.toRadians(90.0).toFloat())
        val actual = body.rawBody!!.getRotation(Quaternionf())
        assertTrue(abs(expected.dot(actual)) > 0.999f, "rotation $actual")
    }

    @Test
    fun teleport_ClearsVelocities() {
        scene.start()
        repeat(20) { physics.fixedUpdate(dt) }
        assertTrue(body.linearVelocity.length() > 0.1f)
        scene.isRunning = false

        transform.translation.set(0f, 10f, 0f)
        physics.onFrame(0f, 1f)

        assertEquals(0f, body.linearVelocity.length(), 1e-4f)
        assertEquals(0f, body.angularVelocity.length(), 1e-4f)
    }

    @Test
    fun editMode_ScaleChange_RebuildsCollider() {
        val oldHandle = body.rawBody
        // Ray straight down at x=0.8: misses the unit box (half extent 0.5), hits it once scaled by 2
        val from = Vector3f(0.8f, 5f, 0f)
        val to = Vector3f(0.8f, -5f, 0f)
        assertNull(world.raycast(from, to))

        transform.scale.set(2f)
        physics.onFrame(0f, 1f)

        assertNotSame(oldHandle, body.rawBody)
        val hit = world.raycast(from, to)
        assertNotNull(hit)
        assertEquals(2f, hit!!.point.y, 0.05f) // top face at 1 + 0.5 * 2
        assertEquals(1, physics.worldBodyCount)
    }

    @Test
    fun undoRedo_TransformCommand_MovesBodyBackAndForth() {
        // Deep snapshots, as the editor builds them (Transform.copy() would share the live vectors)
        val before = Transform().apply { copyFrom(transform) }
        val after = Transform().apply { copyFrom(transform); translation.set(5f, 1f, 0f) }
        val command = TransformCommand(box, before, after)

        command.execute()
        physics.onFrame(0f, 1f)
        assertVec(Vector3f(5f, 1f, 0f), bodyPosition())

        command.undo()
        physics.onFrame(0f, 1f)
        assertVec(Vector3f(0f, 1f, 0f), bodyPosition())

        command.execute()
        physics.onFrame(0f, 1f)
        assertVec(Vector3f(5f, 1f, 0f), bodyPosition())
    }

    @Test
    fun play_StartsFromPoseEditedInSameFrame() {
        transform.translation.set(7f, 20f, 0f) // edited, then Play pressed before any edit-mode frame
        scene.start()

        physics.fixedUpdate(dt)

        val p = bodyPosition()
        assertEquals(7f, p.x, 1e-3f)
        assertTrue(p.y in 19.9f..20f, "y=${p.y}")
    }

    @Test
    fun running_TransformWrittenByOtherSystem_DoesNotTeleport() {
        scene.start()
        repeat(20) { physics.fixedUpdate(dt) }
        val velocity = body.linearVelocity.y
        val y = bodyPosition().y

        transform.translation.set(50f, 50f, 50f) // e.g. MountFollowSystem placing the drawn board
        physics.fixedUpdate(dt)

        assertTrue(bodyPosition().y < y)
        assertTrue(body.linearVelocity.y < velocity)
        assertEquals(0f, bodyPosition().x, 1e-3f)
    }

    @Test
    fun runtimeTeleport_MovesBodyAndRestartsInterpolation() {
        scene.start()
        repeat(5) { physics.fixedUpdate(dt) }

        transform.translation.set(0f, 30f, 0f)
        physics.teleport(box)
        physics.onFrame(0f, 0f) // alpha 0 = previous pose: must not blend from the pre-teleport pose

        assertEquals(30f, transform.translation.y, 1e-3f)
        assertEquals(0f, body.linearVelocity.length(), 1e-4f)
    }

    @Test
    fun editMode_AddedAndRemovedObjects_GainAndLoseBodies() {
        val crate = boxObject("Crate", Vector3f(4f, 0f, 0f))
        scene.addChild(crate)
        physics.invalidateCache()
        physics.onFrame(0f, 1f)
        assertEquals(2, physics.worldBodyCount)

        scene.removeChild(crate)
        physics.invalidateCache()
        physics.onFrame(0f, 1f)
        assertEquals(1, physics.worldBodyCount)
    }
}
