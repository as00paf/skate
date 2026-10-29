package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.physics3d.simple.SimplePhysics3D
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SimplePhysics3DTest {

    private val box = ColliderShape.Box(Vector3f(0.5f))

    @Test
    fun simpleBackend_FactoryCreatesIt() {
        assertTrue(Physics3DFactory.create(mockk(relaxed = true), PhysicsBackend.SIMPLE) is SimplePhysics3D)
    }

    @Test
    fun simpleBackend_DynamicBodyFallsUnderGravity() {
        val world = SimplePhysics3D()
        val body = world.createRigidBody(box, 1f)

        repeat(60) { world.step(1f / 60f) }

        assertEquals(-4.9f, body.getLocation(Vector3f()).y, 0.2f)
    }

    @Test
    fun simpleBackend_BodyRestsOnStaticBody() {
        val world = SimplePhysics3D()
        val floor = world.createRigidBody(box, 0f)
        val body = world.createRigidBody(box, 1f)
        body.setLocation(Vector3f(0f, 3f, 0f))

        repeat(300) { world.step(1f / 60f) }

        assertEquals(floor.getLocation(Vector3f()).y + 2f * box.halfExtents.length(), body.getLocation(Vector3f()).y, 0.1f)
    }

    @Test
    fun simpleBackend_MasksPreventCollision() {
        val world = SimplePhysics3D()
        val floor = world.createRigidBody(box, 0f).also { it.collisionLayer = 2 }
        val body = world.createRigidBody(box, 1f).also {
            it.collisionMask = 1
            it.setLocation(Vector3f(0f, 3f, 0f))
        }

        repeat(300) { world.step(1f / 60f) }

        assertTrue(body.getLocation(Vector3f()).y < floor.getLocation(Vector3f()).y - 1f)
    }

    @Test
    fun simpleBackend_RaycastHitsNearestBodyAndHonoursLayerMask() {
        val world = SimplePhysics3D()
        world.setGravity(Vector3f())
        world.createRigidBody(box, 0f).also { it.setLocation(Vector3f(0f, 0f, 5f)) }
        world.createRigidBody(box, 0f).also { it.setLocation(Vector3f(0f, 0f, 10f)); it.collisionLayer = 4 }

        val hit = world.raycast(Vector3f(), Vector3f(0f, 0f, 20f))
        val onlyFar = world.raycast(Vector3f(), Vector3f(0f, 0f, 20f), layerMask = 4)

        assertEquals(0.2f, hit!!.fraction, 0.05f)
        assertNotNull(onlyFar)
        assertTrue(onlyFar!!.fraction > 0.4f)
        assertNull(world.raycast(Vector3f(0f, 5f, 0f), Vector3f(0f, 5f, 20f)))
    }

    @Test
    fun simpleBackend_JointsAreRejected() {
        assertThrows<UnsupportedOperationException> {
            SimplePhysics3D().addConstraint(mockk(relaxed = true))
        }
    }
}
