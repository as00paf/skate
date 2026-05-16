package com.pafoid.skate.engine.physics3d.constraints

import com.jme3.bullet.joints.HingeJoint
import com.jme3.bullet.joints.PhysicsJoint
import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.utils.JmeVector3f
import org.joml.Vector3f

class HingeConstraint3D(
    val bodyA: PhysicsRigidBody,
    val bodyB: PhysicsRigidBody,
    val pivotA: Vector3f,
    val pivotB: Vector3f,
    val axisA: Vector3f,
    val axisB: Vector3f,
    val limitMin: Float = -1f,
    val limitMax: Float = 1f
) : IPhysicsConstraint {
    override var rawJoint: PhysicsJoint? = null

    override fun createJoint(): PhysicsJoint {
        val joint = HingeJoint(
            bodyA, bodyB,
            JmeVector3f(pivotA.x, pivotA.y, pivotA.z),
            JmeVector3f(pivotB.x, pivotB.y, pivotB.z),
            JmeVector3f(axisA.x, axisA.y, axisA.z),
            JmeVector3f(axisB.x, axisB.y, axisB.z)
        )
        joint.setLimit(limitMin, limitMax)
        rawJoint = joint
        return joint
    }
}
