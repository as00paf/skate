package com.pafoid.skate.engine.physics3d.simple

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.attachComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CapsuleCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.PhysicsContactListener
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/** PHY-25 (AST-17 PHY-b): SimplePhysics3D static oriented boxes (contacts, rays, shape casts) and its broad phase. */
class SimpleStaticBoxesTest {

    private val dt = 1f / 60f

    // --- OrientedBox geometry ---

    @Test
    fun Box_SphereAboveTheTopFace_TouchesWithAnUpNormal() {
        val box = OrientedBox(Vector3f(20f, 0.05f, 2f)).apply { place(Vector3f(), Quaternionf()) }

        val contact = box.sphereContact(Vector3f(15f, 0.5f, 1f), 0.5f)

        assertNotNull(contact)
        assertEquals(1f, contact!!.normal.y, 1e-5f)
        assertEquals(0.05f, contact.depth, 1e-5f)
        assertNull(box.sphereContact(Vector3f(15f, 0.6f, 1f), 0.5f), "just above: no contact")
        // A bounding sphere of this box would reach 20 m up; the real box does not
        assertNull(box.sphereContact(Vector3f(0f, 5f, 0f), 0.5f))
    }

    @Test
    fun Box_Rotated_UsesItsOwnAxes() {
        // A 20 m long, 2 m wide road yawed 45°: a point on its diagonal is on it, one inside its AABB beside it is not
        val box = OrientedBox(Vector3f(10f, 0.05f, 1f)).apply { place(Vector3f(), Quaternionf().rotationY(Math.toRadians(45.0).toFloat())) }

        assertNotNull(box.sphereContact(Vector3f(5f, 0.3f, -5f), 0.3f))
        assertNull(box.sphereContact(Vector3f(5f, 0.3f, 5f), 0.3f))
    }

    @Test
    fun Box_SphereCentreInside_IsPushedOutThroughTheNearestFace() {
        val box = OrientedBox(Vector3f(1f, 1f, 1f)).apply { place(Vector3f(), Quaternionf()) }

        val contact = box.sphereContact(Vector3f(0.9f, 0.2f, 0f), 0.25f)!!

        assertEquals(1f, contact.normal.x, 1e-5f)
        assertEquals(0.1f + 0.25f, contact.depth, 1e-5f)
    }

    @Test
    fun Box_Raycast_HitsTheEntryFaceAndIgnoresStartsInside() {
        val box = OrientedBox(Vector3f(1f, 2f, 1f), localCenter = Vector3f(0f, 2f, 0f)).apply { place(Vector3f(3f, 0f, 0f), Quaternionf()) }

        val down = box.raycast(Vector3f(3f, 10f, 0f), Vector3f(0f, -1f, 0f), 20f)!!
        assertEquals(6f, down.distance, 1e-4f) // top at y = 4
        assertEquals(1f, down.normal.y, 1e-5f)

        val side = box.raycast(Vector3f(0f, 1f, 0f), Vector3f(1f, 0f, 0f), 10f)!!
        assertEquals(2f, side.distance, 1e-4f)
        assertEquals(-1f, side.normal.x, 1e-5f)

        assertNull(box.raycast(Vector3f(3f, 1f, 0f), Vector3f(1f, 0f, 0f), 10f), "starting inside")
        assertNull(box.raycast(Vector3f(3f, 10f, 0f), Vector3f(0f, -1f, 0f), 5f), "too short")
        assertEquals(5.5f, box.raycast(Vector3f(3f, 10f, 0f), Vector3f(0f, -1f, 0f), 20f, inflate = 0.5f)!!.distance, 1e-4f)
    }

    // --- World: contacts ---

    private fun staticBox(world: SimplePhysics3D, at: Vector3f, half: Vector3f, yawDeg: Float = 0f, name: String = "Box") =
        GameObject(name).apply {
            addComponent(Transform(Vector3f(at), rotation = Vector3f(0f, yawDeg, 0f)))
            addComponent(RigidBody3D(0f, BodyType.Static))
            attachComponent(BoxCollider3D(Vector3f(half)))
            world.add(this)
        }

    private fun ball(world: SimplePhysics3D, at: Vector3f, radius: Float = 0.5f) = GameObject("Ball").apply {
        addComponent(Transform(Vector3f(at)))
        addComponent(RigidBody3D(1f))
        attachComponent(BoxCollider3D(Vector3f(radius / sqrt(3f)))) // bounding sphere of exactly [radius]
        world.add(this)
    }

    private fun GameObject.position() = getComponent<RigidBody3D>()!!.getWorldPosition()

    @Test
    fun World_BallRestsOnAThinWideRoadBox() {
        val world = SimplePhysics3D()
        staticBox(world, Vector3f(), Vector3f(20f, 0.05f, 2f))
        val ball = ball(world, Vector3f(12f, 3f, 1f))

        repeat(240) { world.step(dt) }

        assertEquals(0.55f, ball.position().y, 0.03f)
    }

    @Test
    fun World_RotatedRoad_CarriesBallsOnItAndNotBesideIt() {
        val world = SimplePhysics3D()
        staticBox(world, Vector3f(), Vector3f(10f, 0.05f, 1f), yawDeg = 45f)
        val on = ball(world, Vector3f(5f, 2f, -5f), radius = 0.3f)
        val beside = ball(world, Vector3f(5f, 2f, 5f), radius = 0.3f)

        repeat(120) { world.step(dt) }

        assertEquals(0.35f, on.position().y, 0.03f)
        assertTrue(beside.position().y < -1f, "inside the road's AABB but off the road: falls")
    }

    @Test
    fun World_ScaleAndColliderOffsetShapeTheBox() {
        val world = SimplePhysics3D()
        GameObject("Raised").apply {
            addComponent(Transform(Vector3f(), scale = Vector3f(2f, 1f, 2f)))
            addComponent(RigidBody3D(0f, BodyType.Static))
            attachComponent(BoxCollider3D(Vector3f(1f, 0.5f, 1f), offset = Vector3f(0f, 1f, 0f)))
            world.add(this)
        }

        val hit = world.raycast(Vector3f(1.8f, 10f, 0f), Vector3f(1.8f, -10f, 0f))!! // inside x ±2 thanks to the scale

        assertEquals(1.5f, hit.point.y, 1e-3f) // offset 1 + half height 0.5
    }

    @Test
    fun World_TeleportedStaticBox_IsReFiledInTheBroadPhase() {
        val world = SimplePhysics3D()
        val floor = staticBox(world, Vector3f(), Vector3f(2f, 0.5f, 2f))
        world.step(dt)
        floor.getComponent<Transform>()!!.translation.set(100f, 0f, 0f)
        world.teleport(floor)
        val ball = ball(world, Vector3f(100f, 3f, 0f))

        repeat(240) { world.step(dt) }

        assertEquals(1f, ball.position().y, 0.03f)
    }

    @Test
    fun World_StaticTriggerBox_ReportsEnterAndExit() {
        val world = SimplePhysics3D()
        val events = mutableListOf<String>()
        world.contactListener = object : PhysicsContactListener {
            override fun onCollisionStarted(a: GameObject, b: GameObject, point: Vector3f, normal: Vector3f, impulse: Float) {}
            override fun onTriggerEnter(trigger: GameObject, other: GameObject) { events += "enter ${trigger.name}" }
            override fun onTriggerExit(trigger: GameObject, other: GameObject) { events += "exit ${trigger.name}" }
        }
        staticBox(world, Vector3f(0f, 0f, 0f), Vector3f(5f, 0.2f, 5f), name = "Pad").getComponent<RigidBody3D>()!!.rawBody!!.isTrigger = true
        ball(world, Vector3f(0f, 2f, 0f))

        repeat(120) { world.step(dt) }

        assertEquals(listOf("enter Pad", "exit Pad"), events)
    }

    // --- World: queries ---

    @Test
    fun World_RaycastAndShapeCast_HitTheBoxSurfaceNotItsBoundingSphere() {
        val world = SimplePhysics3D()
        val road = staticBox(world, Vector3f(0f, 1f, 0f), Vector3f(20f, 0.1f, 2f))

        val ray = world.raycast(Vector3f(15f, 10f, 0f), Vector3f(15f, -10f, 0f))!!
        assertSame(road, ray.gameObject)
        assertEquals(1.1f, ray.point.y, 1e-4f)
        assertEquals(1f, ray.normal.y, 1e-5f)
        assertNull(world.raycast(Vector3f(0f, 10f, 5f), Vector3f(0f, -10f, 5f)), "beside the road")

        val cast = world.shapeCast(ColliderShape.Capsule(0.25f, 0f, 1), Vector3f(15f, 10f, 0f), Vector3f(15f, -10f, 0f))!!
        assertEquals(1.35f, cast.point.y, 1e-3f) // centre at impact: top + radius
    }

    // --- Broad phase ---

    @Test
    fun BroadPhase_ThousandsOfStaticBoxes_OnlyNearbyOnesAreTested() {
        val world = SimplePhysics3D()
        for (x in 0 until 50) for (z in 0 until 50) staticBox(world, Vector3f(x * 10f, 0f, z * 10f), Vector3f(3f, 3f, 3f))
        ball(world, Vector3f(255f, 10f, 255f))

        world.step(dt)

        assertEquals(2501, world.rigidBodyCount)
        assertTrue(world.lastNarrowPhaseTests < 20, "narrow-phase tests: ${world.lastNarrowPhaseTests}")
    }

    @Test
    fun BroadPhase_HugeGroundBoxIsStillFound() {
        val world = SimplePhysics3D()
        staticBox(world, Vector3f(0f, -0.5f, 0f), Vector3f(2000f, 0.5f, 2000f), name = "Ground")
        val ball = ball(world, Vector3f(1500f, 2f, -1500f))

        repeat(120) { world.step(dt) }

        assertEquals(0.5f, ball.position().y, 0.03f)
        assertNotNull(world.raycast(Vector3f(-1900f, 5f, 1900f), Vector3f(-1900f, -5f, 1900f)))
    }

    // --- A box city (AST-17 Simple fallback) is walkable ---

    @Test
    fun BoxCity_AWalkerStaysOnTheGroundAndRoadsAndIsStoppedByBuildings() {
        val world = SimplePhysics3D()
        // One chunk as AST-17 §4.6 generates it for Simple: a ground box, flat yawed road boxes, one box per building
        staticBox(world, Vector3f(0f, -0.05f, 0f), Vector3f(128f, 0.05f, 128f), name = "Ground")
        staticBox(world, Vector3f(0f, 0.05f, 0f), Vector3f(40f, 0.05f, 3f), name = "Road A")
        staticBox(world, Vector3f(30f, 0.05f, 20f), Vector3f(25f, 0.05f, 3f), yawDeg = 60f, name = "Road B")
        staticBox(world, Vector3f(0f, 6f, 20f), Vector3f(8f, 6f, 6f), yawDeg = 15f, name = "Building 1")
        for (i in 0 until 40) staticBox(world, Vector3f(-60f + i * 3f, 4f, -40f), Vector3f(1f, 4f, 1f), name = "Building ${i + 2}")

        // On Simple a character is its bounding sphere: a 0.3 x 1.2 capsule is a 0.9 m sphere
        val walker = GameObject("Walker").apply {
            addComponent(Transform(Vector3f(-30f, 1f, 0f)))
            addComponent(RigidBody3D(70f))
            attachComponent(CapsuleCollider3D(0.3f, 1.2f))
            world.add(this)
        }
        val body = walker.getComponent<RigidBody3D>()!!
        fun walk(velocity: Vector3f, seconds: Float) = repeat((seconds / dt).toInt()) {
            // Like PlayerMotionSystem: set the horizontal velocity, keep the vertical one
            body.linearVelocity = Vector3f(velocity.x, body.linearVelocity.y, velocity.z)
            world.step(dt)
            assertTrue(body.getWorldPosition().y > 0.5f, "fell through at ${body.getWorldPosition()}")
        }

        walk(Vector3f(), 1f)
        assertEquals(1f, body.getWorldPosition().y, 0.05f, "standing on road A (top 0.1 + 0.9)")

        walk(Vector3f(5f, 0f, 0f), 6f) // along road A
        assertEquals(0f, body.getWorldPosition().x, 0.5f)
        assertEquals(1f, body.getWorldPosition().y, 0.05f)

        walk(Vector3f(0f, 0f, 5f), 6f) // off the road onto the ground, into building 1
        val pos = body.getWorldPosition()
        assertEquals(0.9f, pos.y, 0.05f, "standing on the ground (top 0 + 0.9)")
        assertTrue(pos.z < 14.5f, "stopped by building 1's face, got z = ${pos.z}")

        // The ground probe (GroundDetector raycasts) sees walkable ground under the walker
        val probe = world.raycast(pos, Vector3f(pos).sub(0f, 2f, 0f), excludeBody = body)!!
        assertEquals(1f, probe.normal.y, 1e-4f)
    }
}
