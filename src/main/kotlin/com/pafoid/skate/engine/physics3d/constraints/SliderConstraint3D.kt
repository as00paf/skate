package com.pafoid.skate.engine.physics3d.constraints

import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import org.joml.Vector3f

/** Allows translation along [axisA] (in body A space) between [lowerLimit] and [upperLimit]. */
class SliderConstraint3D(
    val bodyA: IRigidBodyHandle,
    val bodyB: IRigidBodyHandle,
    val pivotA: Vector3f,
    val pivotB: Vector3f,
    val axisA: Vector3f,
    val axisB: Vector3f,
    val lowerLimit: Float = -1f,
    val upperLimit: Float = 1f
) : IPhysicsConstraint {
    override var rawJoint: Any? = null
}
