package com.pafoid.skate.engine.physics3d.constraints

import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import org.joml.Vector3f

/** Per-axis limited linear and angular freedom; equal lower/upper locks the axis, lower > upper frees it. */
class SixDofConstraint3D(
    val bodyA: IRigidBodyHandle,
    val bodyB: IRigidBodyHandle,
    val pivotA: Vector3f,
    val pivotB: Vector3f,
    val linearLower: Vector3f = Vector3f(),
    val linearUpper: Vector3f = Vector3f(),
    val angularLower: Vector3f = Vector3f(),
    val angularUpper: Vector3f = Vector3f()
) : IPhysicsConstraint {
    override var rawJoint: Any? = null
}
