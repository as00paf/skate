package com.pafoid.skate.engine.physics3d.jolt

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.attachComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CapsuleCollider3D
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.CompoundHullCollider3D
import com.pafoid.skate.engine.ecs.components.CustomCollider3D
import com.pafoid.skate.engine.ecs.components.CylinderCollider3D
import com.pafoid.skate.engine.ecs.components.MeshCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.events.PhysicsBackendFellBack
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.Physics3DFactory
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.physics3d.PhysicsBackendFallback
import com.pafoid.skate.engine.physics3d.PhysicsBackends
import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.constraints.FixedConstraint3D
import io.mockk.mockk
import io.mockk.verify
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Jolt-specific behaviour (PHY-17b); the shared contract runs in PhysicsFeaturesTest. */
class JoltPhysics3DTest {

    private val dt = 1f / 60f
    private val worlds = mutableListOf<IPhysics3D>()

    private fun world(backend: PhysicsBackend = PhysicsBackend.JOLT) = PhysicsBackends.create(backend).also { worlds += it }

    @AfterEach
    fun tearDown() {
        worlds.forEach { it.destroy() }
        Physics3DFactory.loadJolt = { if (JoltRuntime.ensureLoaded()) null else JoltRuntime.failure ?: "unknown error" }
    }

    private fun IPhysics3D.body(
        at: Vector3f,
        vararg colliders: Component,
        mass: Float = 1f,
        scale: Float = 1f,
        rotationDeg: Vector3f = Vector3f(),
        configure: RigidBody3D.() -> Unit = {},
    ): GameObject = GameObject("Body").apply {
        addComponent(Transform(Vector3f(at)).apply { this.scale.set(scale); rotation.set(rotationDeg) })
        addComponent(RigidBody3D(mass).apply(configure))
        colliders.forEach { attachComponent(it) }
        add(this)
    }

    private fun IPhysics3D.ground() = body(Vector3f(0f, -0.5f, 0f), BoxCollider3D(Vector3f(50f, 0.5f, 50f)), mass = 0f)

    private fun IPhysics3D.steps(count: Int) = repeat(count) { step(dt) }

    private fun GameObject.rb() = components.filterIsInstance<RigidBody3D>().first()
    private fun GameObject.y() = rb().getWorldPosition().y

    // --- factory ---

    @Test
    fun JoltFactory_CreatesAJoltWorld() {
        assertInstanceOf(JoltPhysics3D::class.java, Physics3DFactory.create(mockk(relaxed = true), PhysicsBackend.JOLT).also { worlds += it })
    }

    @Test
    fun JoltFactory_NativesMissing_FallsBackToBulletAndReportsWhy() {
        Physics3DFactory.loadJolt = { "jolt-jni native library could not be loaded" }
        val reports = mutableListOf<PhysicsBackendFallback>()

        val world = Physics3DFactory.create(mockk(relaxed = true), PhysicsBackend.JOLT) { reports += it }.also { worlds += it }

        assertInstanceOf(BulletPhysics3D::class.java, world)
        assertEquals(listOf(PhysicsBackendFallback(PhysicsBackend.JOLT, PhysicsBackend.BULLET, "jolt-jni native library could not be loaded")), reports)
    }

    @Test
    fun JoltFactory_PhysicsSystemPublishesTheFallback() {
        Physics3DFactory.loadJolt = { "broken" }
        val previous = Physics3DFactory.defaultBackend
        val events = mockk<EventSystem>(relaxed = true)
        try {
            Physics3DFactory.defaultBackend = PhysicsBackend.JOLT
            PhysicsSystem(mockk(relaxed = true), events)
        } finally {
            Physics3DFactory.defaultBackend = previous
        }
        verify { events.publish(PhysicsBackendFellBack(PhysicsBackend.JOLT, PhysicsBackend.BULLET, "broken")) }
    }

    // --- shapes ---

    @Test
    fun JoltShapes_CapsuleAlongX_LiesOnItsSide() {
        val w = world()
        w.ground()
        val capsule = w.body(Vector3f(0f, 1f, 0f), CapsuleCollider3D(radius = 0.3f, height = 1f, axis = 0))
        w.steps(180)
        assertEquals(0.3f, capsule.y(), 0.03f)
    }

    @Test
    fun JoltShapes_CylinderAlongZ_LiesOnItsSide() {
        val w = world()
        w.ground()
        val cylinder = w.body(Vector3f(0f, 1f, 0f), CylinderCollider3D(radius = 0.4f, height = 2f, axis = 2))
        w.steps(180)
        assertEquals(0.4f, cylinder.y(), 0.03f)
    }

    @Test
    fun JoltShapes_ConvexHullCube_RestsOnItsFace() {
        val w = world()
        w.ground()
        val hull = w.body(Vector3f(0f, 1f, 0f), CustomCollider3D(cube(0.5f)))
        w.steps(180)
        assertEquals(0.5f, hull.y(), 0.03f)
    }

    @Test
    fun JoltShapes_OffsetColliders_FormOneCompound() {
        val w = world()
        w.ground()
        w.body(
            Vector3f(0f, 0.5f, 0f),
            BoxCollider3D(Vector3f(0.5f), offset = Vector3f(-1f, 0f, 0f)),
            BoxCollider3D(Vector3f(0.5f), offset = Vector3f(1f, 0f, 0f)),
            mass = 0f,
        )
        // Hits the right box; nothing between the two boxes
        assertEquals(1f, w.raycast(Vector3f(1f, 5f, 0f), Vector3f(1f, -5f, 0f))!!.point.y, 1e-3f)
        assertEquals(0f, w.raycast(Vector3f(0f, 5f, 0f), Vector3f(0f, -5f, 0f))!!.point.y, 1e-3f)
    }

    @Test
    fun JoltShapes_CompoundHull_IsHitOnEachHull() {
        val w = world()
        w.body(Vector3f(), CompoundHullCollider3D(listOf(cube(0.5f, Vector3f(-2f, 0f, 0f)), cube(0.5f, Vector3f(2f, 0f, 0f)))), mass = 0f)
        assertNotNull(w.raycast(Vector3f(2f, 5f, 0f), Vector3f(2f, -5f, 0f)))
        assertNull(w.raycast(Vector3f(0f, 5f, 0f), Vector3f(0f, -5f, 0f)))
    }

    @Test
    fun JoltShapes_TriangleMesh_BodiesRestOnEitherWinding() {
        for (flip in listOf(false, true)) {
            val w = world()
            val a = Vector3f(-10f, 0f, -10f); val b = Vector3f(10f, 0f, -10f)
            val c = Vector3f(10f, 0f, 10f); val d = Vector3f(-10f, 0f, 10f)
            val triangles = if (flip) listOf(Triple(a, b, c), Triple(a, c, d)) else listOf(Triple(a, c, b), Triple(a, d, c))
            w.body(Vector3f(), MeshCollider3D.fromTriangles(triangles), mass = 0f)
            val box = w.body(Vector3f(0f, 2f, 0f), BoxCollider3D(Vector3f(0.5f)))
            w.steps(180)
            assertEquals(0.5f, box.y(), 0.05f, "flipped winding = $flip")
            assertEquals(0f, w.raycast(Vector3f(3f, 5f, 3f), Vector3f(3f, -5f, 3f))!!.point.y, 1e-3f)
        }
    }

    @Test
    fun JoltShapes_TransformScale_ScalesTheBody() {
        val w = world()
        w.ground()
        val box = w.body(Vector3f(0f, 3f, 0f), BoxCollider3D(Vector3f(0.5f)), scale = 2f)
        w.steps(180)
        assertEquals(1f, box.y(), 0.03f)
    }

    // --- bodies ---

    @Test
    fun JoltBodies_LocationIsTheOriginNotTheCentreOfMass() {
        val w = world()
        val go = w.body(Vector3f(1f, 2f, 3f), CustomCollider3D(cube(0.5f, Vector3f(2f, 0f, 0f))), mass = 0f)
        assertEquals(Vector3f(1f, 2f, 3f), go.rb().rawBody!!.getLocation(Vector3f()))
    }

    @Test
    fun JoltBodies_InitialRotation_IsApplied() {
        val w = world()
        val go = w.body(Vector3f(), BoxCollider3D(), mass = 0f, rotationDeg = Vector3f(0f, 90f, 0f))
        val expected = Quaternionf().rotationY(Math.toRadians(90.0).toFloat())
        assertTrue(go.rb().getRotation().equals(expected, 1e-4f))
    }

    @Test
    fun JoltBodies_StandaloneBodiesAndAnchors_AreCountedAndRemoved() {
        val w = world()
        val bone = w.createRigidBody(ColliderShape.Capsule(0.1f, 0.3f, 1), 2f)
        val anchor = w.createStaticAnchor(Vector3f(0f, 5f, 0f))
        assertEquals(2, w.rigidBodyCount)
        assertEquals(2f, bone.mass)
        // Anchors are pivots, invisible to queries
        assertNull(w.raycast(Vector3f(0f, 6f, 0f), Vector3f(0f, 4f, 0f)))

        w.removeRigidBody(bone)
        w.removeRigidBody(anchor)
        w.removeRigidBody(anchor)
        assertEquals(0, w.rigidBodyCount)
    }

    @Test
    fun JoltBodies_KinematicToggle_HoldsThenReleases() {
        val w = world()
        val bone = w.createRigidBody(ColliderShape.Capsule(0.1f, 0.3f, 1), 2f)
        bone.setLocation(Vector3f(0f, 10f, 0f))
        bone.isKinematic = true
        w.steps(30)
        assertEquals(10f, bone.getLocation(Vector3f()).y, 1e-4f)

        bone.isKinematic = false
        w.steps(30)
        assertTrue(bone.getLocation(Vector3f()).y < 9f)
    }

    @Test
    fun JoltBodies_AngularFactor_LocksRotationAxes() {
        val w = world()
        w.setGravity(Vector3f())
        val go = w.body(Vector3f(), BoxCollider3D(Vector3f(0.5f)))
        val handle = go.rb().rawBody!!
        handle.setAngularFactor(Vector3f(0f, 1f, 0f))
        handle.angularVelocity = Vector3f(5f, 5f, 0f)
        w.steps(30)
        val spin = handle.angularVelocity
        assertEquals(0f, spin.x, 1e-3f)
        assertTrue(spin.y > 1f, "rotation about Y stays free, was ${spin.y}")
    }

    @Test
    fun JoltBodies_Teleport_MovesAndClearsVelocity() {
        val w = world()
        val go = w.body(Vector3f(0f, 10f, 0f), BoxCollider3D())
        w.steps(30)
        go.components.filterIsInstance<Transform>().first().translation.set(5f, 20f, 0f)

        w.teleport(go)

        assertEquals(Vector3f(5f, 20f, 0f), go.rb().getWorldPosition())
        assertEquals(0f, go.rb().linearVelocity.length(), 1e-5f)
    }

    @Test
    fun JoltBodies_MassChange_RebuildsTheBody() {
        val w = world()
        val go = w.body(Vector3f(), BoxCollider3D(), mass = 1f)
        val first = go.rb().rawBody
        go.rb().mass = 4f

        w.add(go)

        assertTrue(go.rb().rawBody !== first)
        assertEquals(4f, go.rb().rawBody!!.mass)
        assertEquals(1, w.rigidBodyCount)
    }

    @Test
    fun JoltBodies_MaskChangedAtRuntime_BodyFallsThrough() {
        val w = world()
        w.ground()
        val box = w.body(Vector3f(0f, 0.5f, 0f), BoxCollider3D(Vector3f(0.5f)))
        w.steps(60)
        assertEquals(0.5f, box.y(), 0.05f)

        box.rb().collisionMask = 0
        w.steps(60)

        assertTrue(box.y() < -1f, "box should drop through the ground, y=${box.y()}")
    }

    @Test
    fun JoltBodies_Damping_DecaysLikeBullet() {
        fun speedAfterOneSecond(backend: PhysicsBackend): Float {
            val w = world(backend)
            w.setGravity(Vector3f())
            val go = w.body(Vector3f(), BoxCollider3D()) { linearDamping = 0.5f }
            go.rb().linearVelocity = Vector3f(10f, 0f, 0f)
            w.steps(60)
            return go.rb().linearVelocity.x
        }
        assertEquals(speedAfterOneSecond(PhysicsBackend.BULLET), speedAfterOneSecond(PhysicsBackend.JOLT), 0.25f)
    }

    @Test
    fun JoltBodies_Joints_AreNotSupportedYet() {
        val w = world()
        val a = w.createRigidBody(ColliderShape.Box(Vector3f(0.5f)), 1f)
        val b = w.createRigidBody(ColliderShape.Box(Vector3f(0.5f)), 1f)
        assertThrows(UnsupportedOperationException::class.java) {
            w.addConstraint(FixedConstraint3D(a, b, Vector3f(), Vector3f()))
        }
    }

    // --- queries ---

    @Test
    fun JoltQueries_ShapeCastNormal_FacesTheCaster() {
        val w = world()
        w.body(Vector3f(10f, 0f, 0f), BoxCollider3D(Vector3f(0.5f, 5f, 5f)), mass = 0f)

        val hit = w.shapeCast(ColliderShape.Capsule(0.25f, 0.5f, 1), Vector3f(), Vector3f(20f, 0f, 0f))!!

        assertTrue(hit.normal.x < -0.9f, "normal ${hit.normal}")
        assertEquals(9.25f, hit.point.x, 0.05f)
    }

    @Test
    fun JoltQueries_ShapeCastNonConvex_Throws() {
        val w = world()
        assertThrows(IllegalArgumentException::class.java) {
            w.shapeCast(ColliderShape.TriangleMesh(emptyList(), emptyList()), Vector3f(), Vector3f(1f, 0f, 0f))
        }
    }

    @Test
    fun JoltQueries_RaycastClosest_SkipsExcludedBody() {
        val w = world()
        w.ground()
        val box = w.body(Vector3f(0f, 1f, 0f), BoxCollider3D(Vector3f(0.5f)), mass = 0f)
        val from = Vector3f(0f, 10f, 0f)
        val to = Vector3f(0f, -10f, 0f)

        assertEquals(0.425f, w.raycastClosest(from, to)!!, 1e-3f)
        assertEquals(0.5f, w.raycastClosest(from, to, box.rb())!!, 1e-3f)
    }

    // --- lifecycle ---

    @Test
    fun JoltLifecycle_DestroyTwiceAndStepAfterDestroy_AreSafe() {
        val w = world()
        w.ground()
        w.createRigidBody(ColliderShape.Box(Vector3f(0.5f)), 1f)
        w.destroy()
        w.destroy()
        w.step(dt)
    }

    @Test
    fun JoltLifecycle_ManyWorlds_CreateAndDestroy() {
        repeat(20) {
            val w = Physics3DFactory.create(mockk(relaxed = true), PhysicsBackend.JOLT)
            w.ground()
            w.body(Vector3f(0f, 2f, 0f), BoxCollider3D())
            w.steps(5)
            w.destroy()
        }
    }

    private fun cube(half: Float, center: Vector3f = Vector3f()) = listOf(-1f, 1f).flatMap { x ->
        listOf(-1f, 1f).flatMap { y -> listOf(-1f, 1f).map { z -> Vector3f(x, y, z).mul(half).add(center) } }
    }
}
