package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.constraints.FixedConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.HingeConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.SliderConstraint3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JointConstraintsTest {

    private lateinit var physics: BulletPhysics3D
    private val dt = 1f / 60f
    private val box = ColliderShape.Box(Vector3f(0.25f))

    @BeforeEach
    fun setUp() {
        NativeLibraryLoader().loadNativeLibrary()
        physics = BulletPhysics3D(mockk(relaxed = true))
    }

    @AfterEach
    fun tearDown() = physics.destroy()

    private fun simulate(seconds: Float) = repeat((seconds / dt).toInt()) { physics.step(dt) }

    private fun IRigidBodyHandle.position() = getLocation(Vector3f())

    @Test
    fun joints_FixedToStaticAnchor_HoldsAgainstGravity() {
        val anchor = physics.createStaticAnchor(Vector3f(0f, 5f, 0f))
        val body = physics.createRigidBody(box, 1f).also { it.setLocation(Vector3f(0f, 4f, 0f)) }
        physics.addConstraint(FixedConstraint3D(anchor, body, Vector3f(0f, -1f, 0f), Vector3f()))

        simulate(2f)

        assertEquals(4f, body.position().y, 0.1f)
    }

    @Test
    fun joints_RemovedConstraint_ReleasesBody() {
        val anchor = physics.createStaticAnchor(Vector3f(0f, 5f, 0f))
        val body = physics.createRigidBody(box, 1f).also { it.setLocation(Vector3f(0f, 4f, 0f)) }
        val joint = FixedConstraint3D(anchor, body, Vector3f(0f, -1f, 0f), Vector3f())
        physics.addConstraint(joint)
        simulate(0.5f)

        physics.removeConstraint(joint)
        simulate(1f)

        assertTrue(body.position().y < 0f)
    }

    @Test
    fun joints_Hinge_KeepsPendulumAtConstantDistanceFromPivot() {
        val anchor = physics.createStaticAnchor(Vector3f(0f, 5f, 0f))
        val bob = physics.createRigidBody(box, 1f).also { it.setLocation(Vector3f(2f, 5f, 0f)) }
        physics.addConstraint(
            HingeConstraint3D(
                anchor, bob, Vector3f(), Vector3f(-2f, 0f, 0f),
                Vector3f(0f, 0f, 1f), Vector3f(0f, 0f, 1f), -10f, 10f,
            )
        )

        simulate(1f)

        val pos = bob.position()
        assertTrue(pos.y < 4.5f, "bob should swing down, was ${pos.y}")
        assertEquals(2f, Vector3f(pos).sub(0f, 5f, 0f).length(), 0.1f)
        assertEquals(0f, pos.z, 0.05f)
    }

    @Test
    fun joints_Slider_RestrictsMotionToAxisAndLimits() {
        val anchor = physics.createStaticAnchor(Vector3f(0f, 5f, 0f))
        val body = physics.createRigidBody(box, 1f).also { it.setLocation(Vector3f(0f, 5f, 0f)) }
        physics.addConstraint(
            SliderConstraint3D(anchor, body, Vector3f(), Vector3f(), Vector3f(0f, 1f, 0f), Vector3f(0f, 1f, 0f), -1f, 0f)
        )

        simulate(2f)

        val pos = body.position()
        assertEquals(4f, pos.y, 0.15f)
        assertEquals(0f, pos.x, 0.05f)
        assertEquals(0f, pos.z, 0.05f)
    }
}
