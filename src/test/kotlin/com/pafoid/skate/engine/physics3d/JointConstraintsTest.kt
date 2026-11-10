package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.physics3d.constraints.ConeTwistConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.FixedConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.SixDofConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.HingeConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.SliderConstraint3D
import org.joml.Matrix3f
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class JointConstraintsTest {

    private lateinit var physics: IPhysics3D
    private lateinit var backend: PhysicsBackend
    private val dt = 1f / 60f
    private val box = ColliderShape.Box(Vector3f(0.25f))

    private fun use(backend: PhysicsBackend) {
        this.backend = backend
        physics = PhysicsBackends.create(backend)
    }

    /** Replaces the world with a fresh one on the same backend. */
    private fun fresh() {
        physics.destroy()
        physics = PhysicsBackends.create(backend)
    }

    @AfterEach
    fun tearDown() {
        if (::physics.isInitialized) physics.destroy()
    }

    private fun simulate(seconds: Float) = repeat((seconds / dt).toInt()) { physics.step(dt) }

    private fun IRigidBodyHandle.position() = getLocation(Vector3f())

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun joints_FixedToStaticAnchor_HoldsAgainstGravity(backend: PhysicsBackend) {
        use(backend)
        val anchor = physics.createStaticAnchor(Vector3f(0f, 5f, 0f))
        val body = physics.createRigidBody(box, 1f).also { it.setLocation(Vector3f(0f, 4f, 0f)) }
        physics.addConstraint(FixedConstraint3D(anchor, body, Vector3f(0f, -1f, 0f), Vector3f()))

        simulate(2f)

        assertEquals(4f, body.position().y, 0.1f)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun joints_RemovedConstraint_ReleasesBody(backend: PhysicsBackend) {
        use(backend)
        val anchor = physics.createStaticAnchor(Vector3f(0f, 5f, 0f))
        val body = physics.createRigidBody(box, 1f).also { it.setLocation(Vector3f(0f, 4f, 0f)) }
        val joint = FixedConstraint3D(anchor, body, Vector3f(0f, -1f, 0f), Vector3f())
        physics.addConstraint(joint)
        simulate(0.5f)

        physics.removeConstraint(joint)
        simulate(1f)

        assertTrue(body.position().y < 0f)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun joints_Hinge_KeepsPendulumAtConstantDistanceFromPivot(backend: PhysicsBackend) {
        use(backend)
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

    /** Body A (the moving block, as [com.pafoid.skate.engine.ecs.components.JointComponent] orders it) on a +Y rail. */
    private fun sliderBlock(lower: Float, upper: Float, axis: Vector3f = Vector3f(0f, 1f, 0f)): IRigidBodyHandle {
        val anchor = physics.createStaticAnchor(Vector3f(0f, 5f, 0f))
        val body = physics.createRigidBody(box, 1f).also { it.setLocation(Vector3f(0f, 5f, 0f)) }
        physics.addConstraint(SliderConstraint3D(body, anchor, Vector3f(), Vector3f(), axis, Vector3f(axis), lower, upper))
        return body
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun joints_Slider_RestrictsMotionToAxisAndLimits(backend: PhysicsBackend) {
        use(backend)
        val body = sliderBlock(-1f, 0f)

        simulate(2f)

        val pos = body.position()
        assertEquals(4f, pos.y, 0.15f)
        assertEquals(0f, pos.x, 0.05f)
        assertEquals(0f, pos.z, 0.05f)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun joints_SliderLimits_AreBodyADisplacementAlongItsAxis(backend: PhysicsBackend) {
        use(backend)
        // BUG-019: [-2, 0] on +Y used to pin the falling block at its start
        assertEquals(3f, sliderBlock(-2f, 0f).also { simulate(2f) }.position().y, 0.15f)
        fresh()

        // Upper limit: with gravity pulling up, it stops 1 m above the start
        physics.setGravity(Vector3f(0f, 9.81f, 0f))
        assertEquals(6f, sliderBlock(-2f, 1f).also { simulate(2f) }.position().y, 0.15f)
        fresh()

        // A -Y axis flips the meaning: [0, 2] along -Y also lets it drop 2 m
        assertEquals(3f, sliderBlock(0f, 2f, Vector3f(0f, -1f, 0f)).also { simulate(2f) }.position().y, 0.15f)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun joints_ReAddedAfterRemoval_HoldsAgain(backend: PhysicsBackend) {
        use(backend)
        val anchor = physics.createStaticAnchor(Vector3f(0f, 5f, 0f))
        val body = physics.createRigidBody(box, 1f).also { it.setLocation(Vector3f(0f, 4f, 0f)) }
        val joint = FixedConstraint3D(anchor, body, Vector3f(0f, -1f, 0f), Vector3f())
        physics.addConstraint(joint)
        physics.removeConstraint(joint)

        physics.addConstraint(joint)
        simulate(1f)

        assertEquals(4f, body.position().y, 0.1f)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun joints_ConeTwist_LimitsTheSwing(backend: PhysicsBackend) {
        use(backend)
        val anchor = physics.createStaticAnchor(Vector3f(0f, 5f, 0f))
        val bob = physics.createRigidBody(box, 1f).also { it.setLocation(Vector3f(0f, 4f, 0f)) }
        // Twist axis (frame X) points down the rope; both swings limited to 0.3 rad
        val down = Matrix3f().rotationZ(-Math.PI.toFloat() / 2f)
        physics.addConstraint(
            ConeTwistConstraint3D(anchor, bob, Vector3f(), Vector3f(0f, 1f, 0f), Matrix3f(down), Matrix3f(down), 0.3f, 0.3f, 0.3f)
        )
        bob.linearVelocity = Vector3f(4f, 0f, 0f)

        var maxAngle = 0f
        repeat(120) {
            physics.step(dt)
            val offset = bob.position().sub(0f, 5f, 0f)
            maxAngle = maxOf(maxAngle, Math.atan2(Math.abs(offset.x).toDouble(), -offset.y.toDouble()).toFloat())
        }

        assertTrue(maxAngle in 0.1f..0.5f, "swing angle $maxAngle")
        assertEquals(1f, bob.position().sub(0f, 5f, 0f).length(), 0.1f)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun joints_SixDof_LimitedAxisStopsAtItsLimit(backend: PhysicsBackend) {
        use(backend)
        val anchor = physics.createStaticAnchor(Vector3f(0f, 5f, 0f))
        val body = physics.createRigidBody(box, 1f).also { it.setLocation(Vector3f(0f, 5f, 0f)) }
        // B (the body) may move 1 m down relative to A; everything else locked
        physics.addConstraint(SixDofConstraint3D(anchor, body, Vector3f(), Vector3f(), Vector3f(0f, -1f, 0f), Vector3f()))

        simulate(2f)

        val pos = body.position()
        assertEquals(4f, pos.y, 0.15f)
        assertEquals(0f, pos.x, 0.05f)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun joints_SixDof_LowerAboveUpperFreesTheAxis(backend: PhysicsBackend) {
        use(backend)
        val anchor = physics.createStaticAnchor(Vector3f(0f, 5f, 0f))
        val body = physics.createRigidBody(box, 1f).also { it.setLocation(Vector3f(0f, 5f, 0f)) }
        physics.addConstraint(
            SixDofConstraint3D(anchor, body, Vector3f(), Vector3f(), Vector3f(0f, 1f, 0f), Vector3f(0f, -1f, 0f))
        )

        simulate(1f)

        val pos = body.position()
        assertTrue(pos.y < 2f, "free Y axis should let it fall, y=${pos.y}")
        assertEquals(0f, pos.x, 0.05f)
    }
}
