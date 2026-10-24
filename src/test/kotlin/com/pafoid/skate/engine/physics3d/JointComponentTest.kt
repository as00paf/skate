package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.JointType
import com.pafoid.skate.engine.physics3d.constraints.ConeTwistConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.FixedConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.HingeConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.SixDofConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.SliderConstraint3D
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JointComponentTest {
    private val a = mockk<IRigidBodyHandle>()
    private val b = mockk<IRigidBodyHandle>()

    @Test
    fun Joint_CreateConstraint_MapsEachTypeToItsConstraint() {
        val expected = mapOf(
            JointType.FIXED to FixedConstraint3D::class,
            JointType.HINGE to HingeConstraint3D::class,
            JointType.CONE_TWIST to ConeTwistConstraint3D::class,
            JointType.SLIDER to SliderConstraint3D::class,
            JointType.SIX_DOF to SixDofConstraint3D::class
        )
        expected.forEach { (type, cls) ->
            assertEquals(cls, JointComponent(jointType = type).createConstraint(a, b)::class, type.name)
        }
    }

    @Test
    fun Joint_HingeLimitsAndAnchors_ArePassedToConstraint() {
        val joint = JointComponent(
            jointType = JointType.HINGE, limitMin = -0.5f, limitMax = 1.25f, anchor = Vector3f(1f, 2f, 3f)
        )
        val c = joint.createConstraint(a, b) as HingeConstraint3D
        assertEquals(-0.5f, c.limitMin)
        assertEquals(1.25f, c.limitMax)
        assertEquals(Vector3f(1f, 2f, 3f), c.pivotA)
        assertTrue(c.bodyA === a && c.bodyB === b)
    }

    @Test
    fun Joint_SliderAndConeLimits_ArePassedToConstraint() {
        val slider = JointComponent(jointType = JointType.SLIDER, limitMin = 0f, limitMax = 2f)
            .createConstraint(a, b) as SliderConstraint3D
        assertEquals(0f, slider.lowerLimit)
        assertEquals(2f, slider.upperLimit)

        val cone = JointComponent(jointType = JointType.CONE_TWIST, swingSpan1 = 0.1f, swingSpan2 = 0.2f, twistSpan = 0.3f)
            .createConstraint(a, b) as ConeTwistConstraint3D
        assertEquals(listOf(0.1f, 0.2f, 0.3f), listOf(cone.swingSpan1, cone.swingSpan2, cone.twistSpan))
    }

    @Test
    fun Joint_SixDofLimits_AreCopiedNotShared() {
        val joint = JointComponent(jointType = JointType.SIX_DOF, linearUpper = Vector3f(1f, 2f, 3f))
        val c = joint.createConstraint(a, b) as SixDofConstraint3D
        joint.linearUpper.set(9f)
        assertEquals(Vector3f(1f, 2f, 3f), c.linearUpper)
    }

    @Test
    fun Joint_Serialization_RoundTripsPolymorphically() {
        val serializer = Serializer()
        val original: Component = JointComponent(
            jointType = JointType.SIX_DOF, connectedBodyId = 7, angularLower = Vector3f(-1f, -2f, -3f), limitMax = 4f
        )
        val restored = serializer.decode<Component>(serializer.encode(original))
        assertEquals(original, restored)
    }
}
