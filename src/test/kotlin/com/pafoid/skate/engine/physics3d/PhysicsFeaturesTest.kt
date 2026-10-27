package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PhysicsFeaturesTest {

    private lateinit var physics: BulletPhysics3D
    private val dt = 1f / 60f

    private class Recorder : PhysicsContactListener {
        val collisions = mutableListOf<Pair<GameObject, GameObject>>()
        val normals = mutableListOf<Vector3f>()
        val entered = mutableListOf<Pair<GameObject, GameObject>>()
        val exited = mutableListOf<Pair<GameObject, GameObject>>()

        override fun onCollisionStarted(a: GameObject, b: GameObject, point: Vector3f, normal: Vector3f, impulse: Float) {
            collisions.add(a to b)
            normals.add(normal)
        }

        override fun onTriggerEnter(trigger: GameObject, other: GameObject) { entered.add(trigger to other) }
        override fun onTriggerExit(trigger: GameObject, other: GameObject) { exited.add(trigger to other) }
    }

    @BeforeEach
    fun setUp() {
        NativeLibraryLoader().loadNativeLibrary()
        physics = BulletPhysics3D(mockk<DebugRenderer>(relaxed = true))
    }

    @AfterEach
    fun tearDown() = physics.destroy()

    private fun body(
        name: String,
        pos: Vector3f,
        half: Vector3f,
        mass: Float,
        scale: Float = 1f,
        configure: RigidBody3D.() -> Unit = {},
    ): GameObject {
        val go = GameObject(name)
        go.addComponent(Transform().apply { translation.set(pos); this.scale.set(scale) })
        go.addComponent(RigidBody3D(mass).apply(configure))
        go.addComponent(BoxCollider3D(Vector3f(half)))
        physics.add(go)
        return go
    }

    private fun ground() = body("Ground", Vector3f(0f, -0.5f, 0f), Vector3f(50f, 0.5f, 50f), 0f)

    private fun steps(count: Int) = repeat(count) { physics.step(dt) }

    private fun y(go: GameObject) = go.getComponent(RigidBody3D::class.java).getWorldPosition().y

    private inline fun <reified T : Any> GameObject.getComponent(c: Class<T>): T = components.filterIsInstance<T>().first()

    @Test
    fun raycast_HitsClosestBody_ReportsPointNormalAndObject() {
        val ground = ground()

        val hit = physics.raycast(Vector3f(0f, 10f, 0f), Vector3f(0f, -10f, 0f))

        assertNotNull(hit)
        assertEquals(ground, hit!!.gameObject)
        assertEquals(0f, hit.point.y, 1e-3f)
        assertEquals(0.5f, hit.fraction, 1e-3f)
        assertEquals(1f, hit.normal.y, 1e-3f)
    }

    @Test
    fun raycast_LayerMaskExcludesLayer_ReturnsNull() {
        ground()

        assertNull(physics.raycast(Vector3f(0f, 10f, 0f), Vector3f(0f, -10f, 0f), layerMask = 2))
    }

    @Test
    fun raycast_ExcludedBody_IsSkipped() {
        val ground = ground()
        val box = body("Box", Vector3f(0f, 1f, 0f), Vector3f(0.5f, 0.5f, 0.5f), 1f)
        val from = Vector3f(0f, 10f, 0f)
        val to = Vector3f(0f, -10f, 0f)

        assertEquals(box, physics.raycast(from, to)!!.gameObject)
        assertEquals(ground, physics.raycast(from, to, excludeBody = box.getComponent(RigidBody3D::class.java))!!.gameObject)
    }

    @Test
    fun raycastAll_ReturnsHitsNearestFirst() {
        val ground = ground()
        val box = body("Box", Vector3f(0f, 1f, 0f), Vector3f(0.5f, 0.5f, 0.5f), 0f)

        val hits = physics.raycastAll(Vector3f(0f, 10f, 0f), Vector3f(0f, -10f, 0f))

        assertEquals(listOf(box, ground), hits.map { it.gameObject })
    }

    @Test
    fun shapeCast_SphereLikeBoxHitsWall_ReportsImpactBeforeEnd() {
        val wall = body("Wall", Vector3f(10f, 0f, 0f), Vector3f(0.5f, 5f, 5f), 0f)

        val hit = physics.shapeCast(
            ColliderShape.Box(Vector3f(0.25f, 0.25f, 0.25f)),
            Vector3f(0f, 0f, 0f), Vector3f(20f, 0f, 0f),
        )

        assertEquals(wall, hit!!.gameObject)
        assertTrue(hit.fraction in 0.4f..0.5f, "fraction was ${hit.fraction}")
    }

    @Test
    fun collisionLayers_MaskWithoutOtherLayer_BodiesPassThrough() {
        ground().getComponent(RigidBody3D::class.java).collisionLayer = 1
        val ghost = body("Ghost", Vector3f(0f, 2f, 0f), Vector3f(0.5f, 0.5f, 0.5f), 1f) {
            collisionLayer = 2
            collisionMask = 4
        }

        steps(120)

        assertTrue(y(ghost) < -2f, "ghost should fall through ground, y=${y(ghost)}")
    }

    @Test
    fun collisionLayers_MatchingMask_BodyRestsOnGround() {
        ground()
        val box = body("Box", Vector3f(0f, 2f, 0f), Vector3f(0.5f, 0.5f, 0.5f), 1f) { collisionLayer = 2 }

        steps(180)

        assertEquals(0.5f, y(box), 0.1f)
    }

    @Test
    fun physicsMaterial_Restitution_MakesBodyBounce() {
        body("Ground", Vector3f(0f, -0.5f, 0f), Vector3f(50f, 0.5f, 50f), 0f) { restitution = 1f }
        val bouncy = body("Bouncy", Vector3f(0f, 5f, 0f), Vector3f(0.5f, 0.5f, 0.5f), 1f) { restitution = 1f }
        val dead = body("Dead", Vector3f(20f, 5f, 0f), Vector3f(0.5f, 0.5f, 0.5f), 1f)

        var bouncyMax = 0f
        var deadMax = 0f
        repeat(150) {
            physics.step(dt)
            if (it > 60) {
                bouncyMax = maxOf(bouncyMax, y(bouncy))
                deadMax = maxOf(deadMax, y(dead))
            }
        }

        assertTrue(bouncyMax > 2f, "bouncy rebound height $bouncyMax")
        assertTrue(deadMax < 1f, "dead rebound height $deadMax")
    }

    @Test
    fun physicsMaterial_Friction_SlowsSlidingBody() {
        body("Ground", Vector3f(0f, -0.5f, 0f), Vector3f(500f, 0.5f, 50f), 0f) { friction = 1f }
        val rough = body("Rough", Vector3f(0f, 0.5f, 0f), Vector3f(0.5f, 0.5f, 0.5f), 1f) { friction = 1f }
        val slick = body("Slick", Vector3f(0f, 0.5f, 10f), Vector3f(0.5f, 0.5f, 0.5f), 1f) { friction = 0f }
        rough.getComponent(RigidBody3D::class.java).linearVelocity = Vector3f(10f, 0f, 0f)
        slick.getComponent(RigidBody3D::class.java).linearVelocity = Vector3f(10f, 0f, 0f)

        steps(60)

        assertTrue(rough.getComponent(RigidBody3D::class.java).getWorldPosition().x <
            slick.getComponent(RigidBody3D::class.java).getWorldPosition().x - 1f)
    }

    @Test
    fun contactEvents_BodyLandsOnGround_ReportsSingleCollisionWithUpwardNormal() {
        val recorder = Recorder().also { physics.contactListener = it }
        val ground = ground()
        val box = body("Box", Vector3f(0f, 1f, 0f), Vector3f(0.5f, 0.5f, 0.5f), 1f)

        steps(120)

        assertEquals(listOf(box to ground), recorder.collisions)
        assertTrue(recorder.normals.single().y > 0.9f)
    }

    @Test
    fun triggers_BodyPassesThrough_ReportsEnterAndExitWithoutResponse() {
        val recorder = Recorder().also { physics.contactListener = it }
        val trigger = body("Zone", Vector3f(0f, 5f, 0f), Vector3f(2f, 1f, 2f), 0f) { isTrigger = true }
        val ball = body("Ball", Vector3f(0f, 9f, 0f), Vector3f(0.25f, 0.25f, 0.25f), 1f)

        steps(120)

        assertEquals(listOf(trigger to ball), recorder.entered)
        assertEquals(listOf(trigger to ball), recorder.exited)
        assertTrue(y(ball) < 4f, "ball must fall through the trigger, y=${y(ball)}")
        assertTrue(recorder.collisions.isEmpty())
    }

    @Test
    fun triggers_RaycastsIgnoreTriggerBodies() {
        body("Zone", Vector3f(0f, 5f, 0f), Vector3f(2f, 1f, 2f), 0f) { isTrigger = true }

        assertNull(physics.raycast(Vector3f(0f, 10f, 0f), Vector3f(0f, -10f, 0f)))
    }

    @Test
    fun triggers_RemovedObjectInsideTrigger_ReportsExit() {
        val recorder = Recorder().also { physics.contactListener = it }
        val trigger = body("Zone", Vector3f(0f, 0f, 0f), Vector3f(5f, 5f, 5f), 0f) { isTrigger = true }
        physics.setGravity(Vector3f())
        val box = body("Box", Vector3f(0f, 0f, 0f), Vector3f(0.5f, 0.5f, 0.5f), 1f)
        steps(2)

        physics.remove(box)

        assertEquals(listOf(trigger to box), recorder.exited)
    }

    private fun thinWallScenario(useCCD: Boolean): Float {
        physics.setGravity(Vector3f())
        body("Wall", Vector3f(20f, 0f, 0f), Vector3f(0.05f, 5f, 5f), 0f)
        val board = body("Board", Vector3f(0f, 0f, 0f), Vector3f(0.4f, 0.05f, 0.1f), 1f) { this.useCCD = useCCD }
        board.getComponent(RigidBody3D::class.java).linearVelocity = Vector3f(150f, 0f, 0f)
        steps(40)
        return board.getComponent(RigidBody3D::class.java).getWorldPosition().x
    }

    @Test
    fun ccd_FastBodyAgainstThinWall_DoesNotTunnel() {
        assertTrue(thinWallScenario(useCCD = true) < 20f)
    }

    @Test
    fun ccd_Disabled_FastBodyTunnelsThroughThinWall() {
        assertFalse(thinWallScenario(useCCD = false) < 20f)
    }

    @Test
    fun ccd_SkateboardSizedBodyAtHighSpeed_StopsAtThinWall() {
        physics.setGravity(Vector3f())
        body("Wall", Vector3f(20f, 0f, 0f), Vector3f(0.05f, 5f, 5f), 0f)
        // Same proportions as the Skateboard prefab: a 0.0017 scale on a collider 500x larger than the real size
        val board = body("Board", Vector3f(0f, 0f, 0f), Vector3f(200f, 10f, 50f), 1.8f, scale = 0.0017f) { useCCD = true }
        board.getComponent(RigidBody3D::class.java).linearVelocity = Vector3f(60f, 0f, 0f)

        steps(60)

        assertTrue(board.getComponent(RigidBody3D::class.java).getWorldPosition().x < 20f)
    }
}
