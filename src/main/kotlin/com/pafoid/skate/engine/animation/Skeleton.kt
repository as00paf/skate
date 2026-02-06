package com.pafoid.skate.engine.animation

import org.joml.Matrix4f

class Skeleton(
    val rootJoint: Joint,
    val jointCount: Int,
    val rootTransform: Matrix4f = Matrix4f()
) {
    private val joints = arrayOfNulls<Joint>(jointCount)
    private val matrixPalette = Array(jointCount) { Matrix4f() }

    init {
        addJointToMap(rootJoint)
    }

    private fun addJointToMap(joint: Joint) {
        if (joint.index in 0 until jointCount) {
            joints[joint.index] = joint
        }
        for (child in joint.children) {
            addJointToMap(child)
        }
    }

    fun update() {
        rootJoint.calculateWorldTransforms(rootTransform)
        for (i in 0 until jointCount) {
            val joint = joints[i]
            if (joint != null) {
                // finalMatrix = worldTransform * inverseBindMatrix
                joint.worldTransform.mul(joint.inverseBindMatrix, matrixPalette[i])
            } else {
                matrixPalette[i].identity()
            }
        }
    }

    fun getMatrixPalette(): Array<Matrix4f> = matrixPalette
    
    fun getJointByName(name: String): Joint? {
        return joints.find { it?.name == name }
    }

    fun getAllJoints(): List<Joint> {
        return joints.filterNotNull()
    }

    fun copy(): Skeleton {
        return Skeleton(rootJoint.copy(), jointCount, Matrix4f(rootTransform))
    }
}
