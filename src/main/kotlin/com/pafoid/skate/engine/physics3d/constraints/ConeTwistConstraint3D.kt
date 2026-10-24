package com.pafoid.skate.engine.physics3d.constraints

import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import org.joml.Matrix3f
import org.joml.Vector3f

class ConeTwistConstraint3D(
    val bodyA: IRigidBodyHandle,
    val bodyB: IRigidBodyHandle,
    val pivotA: Vector3f,
    val pivotB: Vector3f,
    val rotA: Matrix3f = Matrix3f(),
    val rotB: Matrix3f = Matrix3f(),
    val swingSpan1: Float = 1f,
    val swingSpan2: Float = 1f,
    val twistSpan: Float = 1f
) : IPhysicsConstraint {
    override var rawJoint: Any? = null
}
