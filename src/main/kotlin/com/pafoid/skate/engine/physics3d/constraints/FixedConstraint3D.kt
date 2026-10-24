package com.pafoid.skate.engine.physics3d.constraints

import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import org.joml.Vector3f

/** Locks all relative movement between two bodies. */
class FixedConstraint3D(
    val bodyA: IRigidBodyHandle,
    val bodyB: IRigidBodyHandle,
    val pivotA: Vector3f,
    val pivotB: Vector3f
) : IPhysicsConstraint {
    override var rawJoint: Any? = null
}
