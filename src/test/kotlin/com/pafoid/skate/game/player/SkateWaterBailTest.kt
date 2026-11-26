package com.pafoid.skate.game.player

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.GroundStateComponent
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.ecs.systems.BuoyancySystem
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.ecs.systems.PlayerMotionSystem
import com.pafoid.skate.engine.environment.WaterQueries
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.engine.gameplay.WaterResponse
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.game.prefabs.Skater
import io.mockk.every
import io.mockk.mockk
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * GPL-32: riding into a pool (a [WaterComponent] volume) publishes PHY-16's `WaterEntered` through the real
 * [BuoyancySystem]; a player with [WaterResponse.BAIL] bails, then respawns standing still.
 */
class SkateWaterBailTest {
    private val dt = 1f / 60f
    private val poolCenter = Vector3f(20f, 0f, 0f)

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

    private inner class Rig(response: WaterResponse, spawnAt: Vector3f?, spawnYaw: Float = 0f) {
        val scene = Scene("water").apply { isRunning = true }
        val events = EventSystem()
        val handle = FakeHandle(Vector3f(0f, 0.91f, 0f))
        val skater = GameObject("Skater")
            .addComponent(Transform())
            .addComponent(RigidBody3D(80f).apply { rawBody = handle })
            .addComponent(BoxCollider3D(Vector3f(0.2f, 0.91f, 0.2f)))
            .addComponent(InputStateComponent())
            .addComponent(GroundStateComponent())
            .addComponent(PlayerController(SkateBehavior.ID, Skater.CONFIG_PATH, response))
        val pool = GameObject("Pool")
            .addComponent(Transform(Vector3f(poolCenter)).also { it.worldMatrix.translation(poolCenter) })
            .addComponent(WaterComponent(sizeX = 10f, sizeZ = 10f, depth = 3f))
        val buoyancy = BuoyancySystem(events)
        val waters = WaterQueries()
        val system: PlayerMotionSystem

        val behavior get() = skater.getComponent<PlayerController>()!!.behavior as SkateBehavior

        init {
            SkateGameplay.register()
            scene.addChild(skater)
            scene.addChild(pool)
            spawnAt?.let { at ->
                scene.addChild(GameObject("SpawnPoint").apply {
                    tag = PrefabsGenerator.SPAWN_POINT_TAG
                    addComponent(Transform(Vector3f(at)).also { it.worldMatrix.translation(at).rotateY(spawnYaw) })
                })
            }
            val cameras = mockk<CameraManager>(relaxed = true)
            every { cameras.camera } returns CameraComponent()
            system = PlayerMotionSystem(cameras).also { it.init(scene) }
            system.listenTo(events)
            system.listenTo(events) // a second call (both hosts call install) must stay harmless
            waters.refresh(listOf(pool), null)
            behavior.mounted = true
        }

        /** One frame: the physics step (water events), then the motion system. */
        fun frame() {
            buoyancy.step(listOf(skater), waters, 0f, dt, Vector3f(0f, -9.81f, 0f))
            system.update(dt)
        }

        /** Rides at 6 m/s along +X until the body is chest-deep in the pool. */
        fun rideIntoPool() {
            frame() // records the Play-start pose on dry land
            handle.linearVelocity = Vector3f(6f, 0f, 0f)
            handle.location.set(poolCenter.x, -0.5f, 0f)
            frame()
        }
    }

    @Test
    fun WaterBail_RidingIntoAPoolBailsThenRespawnsAtTheSpawnPoint() {
        val spawn = Vector3f(-5f, 1f, 3f)
        val r = Rig(WaterResponse.BAIL, spawn, spawnYaw = 1.2f)
        r.rideIntoPool()
        assertEquals(SkateState.BAIL.name, r.behavior.state)

        val frames = (r.behavior.config.bail.recoverySec / dt).toInt() + 2
        repeat(frames) { r.frame() }

        assertEquals(spawn, r.handle.location)
        assertEquals(1.2f, MotionMath.yawOfRotation(r.handle.rotation), 1e-4f)
        assertEquals(0f, r.handle.linearVelocity.length(), 1e-4f)
        assertEquals(SkateState.IDLE.name, r.behavior.state)
    }

    @Test
    fun WaterBail_WithoutASpawnPointRespawnsWherePlayStarted() {
        val r = Rig(WaterResponse.BAIL, spawnAt = null)
        r.rideIntoPool()
        repeat((r.behavior.config.bail.recoverySec / dt).toInt() + 2) { r.frame() }
        assertEquals(Vector3f(0f, 0.91f, 0f), r.handle.location)
    }

    @Test
    fun WaterBail_NoRespawnBeforeTheBailIsOver() {
        val r = Rig(WaterResponse.BAIL, Vector3f(-5f, 1f, 3f))
        r.rideIntoPool()
        repeat((r.behavior.config.bail.recoverySec / dt).toInt() / 2) { r.frame() }
        assertEquals(poolCenter.x, r.handle.location.x)
        assertEquals(SkateState.BAIL.name, r.behavior.state)
    }

    @Test
    fun WaterBail_NonePolicyKeepsRiding() {
        val r = Rig(WaterResponse.NONE, Vector3f(-5f, 1f, 3f))
        r.rideIntoPool()
        repeat((r.behavior.config.bail.recoverySec / dt).toInt() + 2) { r.frame() }
        assertNotEquals(SkateState.BAIL.name, r.behavior.state)
        assertTrue(r.handle.location.x > 10f, "still in or past the pool, not respawned")
    }

    @Test
    fun WaterBail_OnFootRespawnsAtOnce() {
        val r = Rig(WaterResponse.BAIL, Vector3f(-5f, 1f, 3f))
        r.behavior.mounted = false
        r.rideIntoPool()
        r.frame()
        assertEquals(Vector3f(-5f, 1f, 3f), r.handle.location)
    }

    @Test
    fun WaterBail_SkaterPrefabBailsInWater() {
        val model = com.pafoid.skate.engine.assets.data.models.`3dModel`(
            skeleton = com.pafoid.skate.engine.assets.data.models.animations.Skeleton(
                com.pafoid.skate.engine.assets.data.models.animations.Bone(0, "root"), 1,
            ),
        )
        assertEquals(WaterResponse.BAIL, Skater("Skater", model).getComponent<PlayerController>()!!.waterResponse)
    }
}
