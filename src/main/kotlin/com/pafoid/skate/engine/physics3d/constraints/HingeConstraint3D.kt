package com.pafoid.skate.engine.physics3d.constraints

import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import org.joml.Vector3f

class HingeConstraint3D(
    val bodyA: IRigidBodyHandle,
    val bodyB: IRigidBodyHandle,
    val pivotA: Vector3f,
    val pivotB: Vector3f,
    val axisA: Vector3f,
    val axisB: Vector3f,
    val limitMin: Float = -1f,
    val limitMax: Float = 1f
) : IPhysicsConstraint {
    override var rawJoint: Any? = null
}
