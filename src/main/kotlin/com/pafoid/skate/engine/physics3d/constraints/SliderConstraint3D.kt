package com.pafoid.skate.engine.physics3d.constraints

import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import org.joml.Vector3f

/**
 * Allows translation along [axisA] (in body A space). [lowerLimit] and [upperLimit] bound body A's displacement
 * relative to body B along that axis, from where the pivots coincide: [-2, 0] lets A slide up to 2 m towards -axisA.
 */
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
