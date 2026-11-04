package com.pafoid.skate.game.player

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.ecs.systems.MountFollowSystem
import com.pafoid.skate.engine.ecs.systems.PlayerMotionSystem
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.engine.gameplay.MountMath
import com.pafoid.skate.engine.gameplay.ground.GroundInfo
import com.pafoid.skate.engine.gameplay.ground.GroundConfig
import com.pafoid.skate.engine.gameplay.ground.GroundDetector
import com.pafoid.skate.engine.physics3d.ALL_LAYERS
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.engine.physics3d.simple.SimplePhysics3D
import com.pafoid.skate.game.prefabs.Skater
import io.mockk.every
import io.mockk.mockk
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

class MountPoseTest {

    private val offset = Vector3f(0.05f, -0.05f, 0.1f)

    private fun assertVec(expected: Vector3f, actual: Vector3f, eps: Float = 1e-5f) {
        assertEquals(expected.x, actual.x, eps, "x of $actual")
        assertEquals(expected.y, actual.y, eps, "y of $actual")
        assertEquals(expected.z, actual.z, eps, "z of $actual")
    }

    @Test
    fun MountPose_BoardSitsAtOffsetInRiderHeadingFrame() {
        val feet = Vector3f(1f, 0.1f, 2f)
        assertVec(Vector3f(1.05f, 0.05f, 2.1f), MountMath.mountPosition(feet, 0f, offset))
        // Heading +X (yaw 90 deg): local forward z maps to +X, local x to -Z
        assertVec(Vector3f(1.1f, 0.05f, 1.95f), MountMath.mountPosition(feet, (PI / 2).toFloat(), offset))
    }

    @Test
    fun MountPose_RiderFeetInvertMountPosition() {
        val board = Vector3f(3f, 0.06f, -1f)
        for (yaw in listOf(0f, 1f, -2.5f, PI.toFloat())) {
            val feet = MountMath.riderFeet(board, yaw, offset)
            assertVec(board, MountMath.mountPosition(feet, yaw, offset))
        }
        // Feet stand on the deck: half the board thickness above its centre
        assertEquals(0.11f, MountMath.riderFeet(board, 0f, Vector3f(0f, -0.05f, 0f)).y, 1e-6f)
    }

    @Test
    fun MountPose_RidingAtRideHeightCountsAsGrounded() {
        val atDeck = GroundInfo(found = true, distance = 0.1f, grounded = false)
        assertTrue(MountMath.grounded(atDeck, mounted = true, rideHeight = 0.1f, groundedDistance = 0.05f))
        assertFalse(MountMath.grounded(atDeck, mounted = false, rideHeight = 0.1f, groundedDistance = 0.05f))
        val ollie = GroundInfo(found = true, distance = 0.4f)
        assertFalse(MountMath.grounded(ollie, mounted = true, rideHeight = 0.1f, groundedDistance = 0.05f))
        assertFalse(MountMath.grounded(GroundInfo(), mounted = true, rideHeight = 0.1f, groundedDistance = 0.05f))
    }

    @Test
    fun MountPhysics_RiddenBoardIsKinematicAndCollidesWithNothing() {
        val ridden = MountMath.mountBodyState(true)
        assertTrue(ridden.kinematic)
        assertEquals(0, ridden.collisionMask)
        val free = MountMath.mountBodyState(false)
        assertFalse(free.kinematic)
        assertEquals(-1, free.collisionMask)
    }

    @Test
    fun MountFollow_PlacesBoardUnderRiderTransform() {
        val rider = Transform(Vector3f(1f, 0.1f, 2f), Vector3f(1f, 1f, 1f), Vector3f(0f, 90f, 0f))
        val board = Transform()
        MountFollowSystem().follow(rider, board, offset)
        assertVec(Vector3f(1.1f, 0.05f, 1.95f), board.translation, 1e-4f)
        val q = Quaternionf().rotationXYZ(
            Math.toRadians(board.rotation.x.toDouble()).toFloat(),
            Math.toRadians(board.rotation.y.toDouble()).toFloat(),
            Math.toRadians(board.rotation.z.toDouble()).toFloat(),
        )
        // 90 deg is the Euler XYZ singularity, so allow a little float error there
        assertEquals((PI / 2).toFloat(), MotionMath.yawOfRotation(q), 1e-3f)

        // A heading past 90 deg survives the Euler round trip (decomposes as x = z = 180)
        rider.rotation.set(0f, 135f, 0f)
        MountFollowSystem().follow(rider, board, offset)
        assertVec(MountMath.mountPosition(rider.translation, Math.toRadians(135.0).toFloat(), offset), board.translation, 1e-4f)
    }

    @Test
    fun MountPhysics_GroundProbeWithCharacterMaskIgnoresRiddenBoard() {
        // Root cause of the floating rider: the probe hit the board under the feet (distance ~0), so the ride-height
        // hover kept lifting the rider and the carried board with it. Probing with the character's mask skips it.
        val world = SimplePhysics3D().apply { setGravity(Vector3f()) }
        world.createRigidBody(ColliderShape.Box(Vector3f(0.05f)), 0f).apply {
            setLocation(Vector3f(0f, -0.05f, 0f))
            collisionLayer = MountMath.MOUNT_LAYER
        }
        fun probe(mask: Int) = GroundDetector(GroundConfig(sampleRadius = 0f)) { from, to -> world.raycast(from, to, mask)?.fraction }
            .probe(Vector3f())

        assertTrue(probe(ALL_LAYERS).found)
        assertFalse(probe(MountMath.characterMask(ALL_LAYERS)).found)
    }

    // --- PlayerMotionSystem: mount snap and physics state switch ------------------------------------

    private class FakeHandle(location: Vector3f, yaw: Float = 0f) : IRigidBodyHandle {
        val location = Vector3f(location)
        val rotation = Quaternionf().rotationY(yaw)
        override val mass = 1f
        override val linearDamping = 0f
        override val angularDamping = 0f
        override var friction = 0f
        override var restitution = 0f
        override var collisionLayer = 1
        override var collisionMask = -1
        override var isTrigger = false
        override var isKinematic = false
        override var linearVelocity = Vector3f()
        override var angularVelocity = Vector3f()
        override fun setContinuousCollision(motionThreshold: Float, sweptRadius: Float) {}
        override fun setDamping(linear: Float, angular: Float) {}
        override fun setAngularFactor(factor: Vector3f) {}
        override fun applyCentralForce(force: Vector3f) {}
        override fun applyImpulse(impulse: Vector3f, relPos: Vector3f) {}
        override fun applyTorqueImpulse(torque: Vector3f) {}
        override fun applyForce(force: Vector3f, relPos: Vector3f) {}
        override fun getLocation(out: Vector3f): Vector3f = out.set(location)
        override fun setLocation(location: Vector3f) { this.location.set(location) }
        override fun getRotation(out: Quaternionf): Quaternionf = out.set(rotation)
        override fun setRotation(rotation: Quaternionf) { this.rotation.set(rotation) }
    }

    private class Rig(val scene: Scene, val system: PlayerMotionSystem, val skater: GameObject, val rider: FakeHandle, val board: FakeHandle) {
        val behavior get() = skater.getComponentOf<PlayerController>().behavior as SkateBehavior
        val input get() = skater.getComponentOf<InputStateComponent>()
    }

    private fun rig(boardAt: Vector3f, boardYaw: Float): Rig {
        SkateGameplay.register()
        val scene = Scene("test").apply { isRunning = true }
        val rider = FakeHandle(Vector3f(0f, 0f, 0f))
        val board = FakeHandle(boardAt, boardYaw)
        val skater = GameObject("Skater")
            .addComponent(Transform())
            .addComponent(RigidBody3D(80f).apply { rawBody = rider })
            .addComponent(InputStateComponent())
            .addComponent(PlayerController(SkateBehavior.ID, Skater.CONFIG_PATH))
        val skateboard = GameObject("Skateboard")
            .addComponent(Transform())
            .addComponent(RigidBody3D(2f).apply { rawBody = board })
        scene.addChild(skater)
        scene.addChild(skateboard)
        val cameras = mockk<CameraManager>(relaxed = true)
        every { cameras.camera } returns CameraComponent()
        val system = PlayerMotionSystem(cameras).also { it.init(scene) }
        return Rig(scene, system, skater, rider, board)
    }

    @Test
    fun MountSwitch_MountingSnapsRiderOntoDeckAndCarriesKinematicBoard() {
        val yaw = 0.7f
        val r = rig(Vector3f(0.5f, 0.06f, 0.8f), yaw)
        r.input.mountPressed = true
        r.system.update(0.016f)

        assertTrue(r.behavior.mounted)
        assertEquals(yaw, MotionMath.yawOfRotation(r.rider.rotation), 1e-5f)
        // Board stays where it was and the rider's feet are on its deck
        assertVec(Vector3f(0.5f, 0.06f, 0.8f), r.board.location)
        assertVec(MountMath.riderFeet(r.board.location, yaw, r.behavior.mountOffset), r.rider.location)
        assertTrue(r.board.isKinematic)
        assertEquals(0, r.board.collisionMask)
        assertEquals(MountMath.MOUNT_LAYER, r.board.collisionLayer)
        assertEquals(0, r.rider.collisionMask and MountMath.MOUNT_LAYER)
    }

    @Test
    fun MountSwitch_DismountMakesBoardDynamicAgain() {
        val r = rig(Vector3f(0.5f, 0.06f, 0.8f), 0f)
        r.input.mountPressed = true
        r.system.update(0.016f)
        r.input.mountPressed = false
        r.system.update(0.016f)
        assertTrue(r.board.isKinematic)

        r.input.mountPressed = true
        r.system.update(0.016f)

        assertFalse(r.behavior.mounted)
        assertFalse(r.board.isKinematic)
        assertEquals(-1, r.board.collisionMask)
    }
}

private inline fun <reified T : com.pafoid.skate.engine.ecs.components.Component> GameObject.getComponentOf(): T =
    components.filterIsInstance<T>().first()
