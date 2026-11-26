package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.GroundStateComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.gameplay.ground.GroundQueries
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.engine.physics3d.PhysicsHit
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroundProbeSystemTest {

    private class FlatFloor(val surface: GameObject? = null, val surfaceNormalY: Float = 1f) : GroundQueries {
        private fun hit(from: Vector3f, to: Vector3f, normalY: Float): PhysicsHit? {
            if (from.y < 0f || to.y > 0f) return null
            return PhysicsHit(surface, Vector3f(from.x, 0f, from.z), Vector3f(0f, normalY, 0f), from.y / (from.y - to.y))
        }

        override fun raycast(from: Vector3f, to: Vector3f, layerMask: Int, exclude: IPhysicsBody3D?): PhysicsHit? = hit(from, to, 1f)

        override fun sphereCast(radius: Float, from: Vector3f, to: Vector3f, layerMask: Int, exclude: IPhysicsBody3D?): PhysicsHit? =
            hit(from, to, surfaceNormalY)
    }

    private class FakeHandle(location: Vector3f) : IRigidBodyHandle {
        val location = Vector3f(location)
        val rotation = Quaternionf()
        override val mass = 80f
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

    private fun character(height: Float): Pair<Scene, GroundStateComponent> {
        val handle = FakeHandle(Vector3f(0f, height, 0f))
        val body = RigidBody3D(80f).also { it.rawBody = handle }
        val state = GroundStateComponent()
        val scene = Scene("Park").apply { isRunning = true }
        scene.addChild(GameObject("Skater").addComponent(Transform()).addComponent(body).addComponent(state))
        return scene to state
    }

    private fun probe(scene: Scene, queries: GroundQueries) {
        GroundProbeSystem(queries).also { it.init(scene) }.fixedUpdate(1f / 60f)
    }

    @Test
    fun GroundProbe_FeetOnTheFloor_AreStanding() {
        val (scene, state) = character(0.02f)
        probe(scene, FlatFloor())
        assertTrue(state.isGrounded)
        assertEquals(0.02f, state.ground.distance, 1e-4f)
        assertNull(state.groundTag)
    }

    @Test
    fun GroundProbe_FeetHighAbove_AreNotStanding() {
        val (scene, state) = character(2f)
        probe(scene, FlatFloor())
        assertFalse(state.isGrounded)
    }

    @Test
    fun GroundProbe_KeepsThePreviousFramesGroundedFlag() {
        val (scene, state) = character(0.02f)
        val system = GroundProbeSystem(FlatFloor()).also { it.init(scene) }
        system.fixedUpdate(1f / 60f)
        assertFalse(state.wasGrounded)
        system.fixedUpdate(1f / 60f)
        assertTrue(state.wasGrounded)
    }

    @Test
    fun GroundProbe_TagComesFromASurfaceHitFromAbove() {
        val rail = GameObject("Rail").also { it.tag = "GRINDABLE" }
        val (scene, state) = character(0.02f)
        probe(scene, FlatFloor(rail))
        assertEquals("GRINDABLE", state.groundTag)
    }

    @Test
    fun GroundProbe_TagIgnoresASideHit() {
        val rail = GameObject("Rail").also { it.tag = "GRINDABLE" }
        val (scene, state) = character(0.02f)
        probe(scene, FlatFloor(rail, surfaceNormalY = 0.1f))
        assertNull(state.groundTag)
    }

    @Test
    fun GroundProbe_TuningFieldsRoundTripThroughTheConfig() {
        val state = GroundStateComponent(stepHeight = 0.7f, snapToGround = false)
        assertEquals(0.7f, state.config.stepHeight)
        assertFalse(state.config.snapToGround)
        val copy = GroundStateComponent.of(state.config)
        assertEquals(state.config, copy.config)
    }
}
