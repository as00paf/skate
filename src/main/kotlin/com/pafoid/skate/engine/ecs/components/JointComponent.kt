package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.engine.physics3d.constraints.ConeTwistConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.FixedConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.HingeConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.IPhysicsConstraint
import com.pafoid.skate.engine.physics3d.constraints.SixDofConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.SliderConstraint3D
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f

enum class JointType { FIXED, HINGE, CONE_TWIST, SLIDER, SIX_DOF }

/**
 * Connects this object's [RigidBody3D] to another object's rigid body (by [connectedBodyId], the GameObject uId).
 * With no connected body (-1) the joint is anchored to a static point in the world.
 * Angles are in radians.
 */
@Serializable
data class JointComponent(
    var jointType: JointType = JointType.FIXED,
    var connectedBodyId: Int = -1,
    @Contextual val anchor: Vector3f = Vector3f(),
    @Contextual val connectedAnchor: Vector3f = Vector3f(),
    @Contextual val axis: Vector3f = Vector3f(0f, 1f, 0f),
    @Contextual val connectedAxis: Vector3f = Vector3f(0f, 1f, 0f),
    // Hinge angle range, or slider travel range
    var limitMin: Float = -1f,
    var limitMax: Float = 1f,
    // Cone-twist
    var swingSpan1: Float = 0.5f,
    var swingSpan2: Float = 0.5f,
    var twistSpan: Float = 0.5f,
    // 6DOF
    @Contextual val linearLower: Vector3f = Vector3f(),
    @Contextual val linearUpper: Vector3f = Vector3f(),
    @Contextual val angularLower: Vector3f = Vector3f(),
    @Contextual val angularUpper: Vector3f = Vector3f()
) : Component() {

    @Transient
    var constraint: IPhysicsConstraint? = null

    fun createConstraint(bodyA: IRigidBodyHandle, bodyB: IRigidBodyHandle): IPhysicsConstraint = when (jointType) {
        JointType.FIXED -> FixedConstraint3D(bodyA, bodyB, Vector3f(anchor), Vector3f(connectedAnchor))
        JointType.HINGE -> HingeConstraint3D(
            bodyA, bodyB, Vector3f(anchor), Vector3f(connectedAnchor),
            Vector3f(axis), Vector3f(connectedAxis), limitMin, limitMax
        )
        JointType.CONE_TWIST -> ConeTwistConstraint3D(
            bodyA, bodyB, Vector3f(anchor), Vector3f(connectedAnchor),
            swingSpan1 = swingSpan1, swingSpan2 = swingSpan2, twistSpan = twistSpan
        )
        JointType.SLIDER -> SliderConstraint3D(
            bodyA, bodyB, Vector3f(anchor), Vector3f(connectedAnchor),
            Vector3f(axis), Vector3f(connectedAxis), limitMin, limitMax
        )
        JointType.SIX_DOF -> SixDofConstraint3D(
            bodyA, bodyB, Vector3f(anchor), Vector3f(connectedAnchor),
            Vector3f(linearLower), Vector3f(linearUpper), Vector3f(angularLower), Vector3f(angularUpper)
        )
    }
}
