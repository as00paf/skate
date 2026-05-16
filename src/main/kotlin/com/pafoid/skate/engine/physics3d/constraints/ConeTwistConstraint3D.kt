package com.pafoid.skate.engine.physics3d.constraints

import com.jme3.bullet.joints.ConeJoint
import com.jme3.bullet.joints.PhysicsJoint
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Matrix3f
import com.pafoid.skate.engine.utils.JmeVector3f
import org.joml.Vector3f

class ConeTwistConstraint3D(
    val bodyA: PhysicsRigidBody,
    val bodyB: PhysicsRigidBody,
    val pivotA: Vector3f,
    val pivotB: Vector3f,
    val rotA: org.joml.Matrix3f = org.joml.Matrix3f(),
    val rotB: org.joml.Matrix3f = org.joml.Matrix3f(),
    val swingSpan1: Float = 1f,
    val swingSpan2: Float = 1f,
    val twistSpan: Float = 1f
) : IPhysicsConstraint {
    override var rawJoint: PhysicsJoint? = null

    override fun createJoint(): PhysicsJoint {
        val matA = Matrix3f()
        matA.set(0, 0, rotA.m00); matA.set(0, 1, rotA.m01); matA.set(0, 2, rotA.m02)
        matA.set(1, 0, rotA.m10); matA.set(1, 1, rotA.m11); matA.set(1, 2, rotA.m12)
        matA.set(2, 0, rotA.m20); matA.set(2, 1, rotA.m21); matA.set(2, 2, rotA.m22)

        val matB = Matrix3f()
        matB.set(0, 0, rotB.m00); matB.set(0, 1, rotB.m01); matB.set(0, 2, rotB.m02)
        matB.set(1, 0, rotB.m10); matB.set(1, 1, rotB.m11); matB.set(1, 2, rotB.m12)
        matB.set(2, 0, rotB.m20); matB.set(2, 1, rotB.m21); matB.set(2, 2, rotB.m22)

        val joint = ConeJoint(
            bodyA, bodyB,
            JmeVector3f(pivotA.x, pivotA.y, pivotA.z),
            JmeVector3f(pivotB.x, pivotB.y, pivotB.z),
            matA, matB
        )
        joint.setLimit(swingSpan1, swingSpan2, twistSpan)
        rawJoint = joint
        return joint
    }
}
