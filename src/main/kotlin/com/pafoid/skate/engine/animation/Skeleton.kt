package com.pafoid.skate.engine.animation

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f

@Serializable
class Skeleton(
    val rootJoint: Joint,
    val jointCount: Int
) {
    private val joints = arrayOfNulls<Joint>(jointCount)
    private val matrixPalette = Array<@Contextual Matrix4f>(jointCount) { Matrix4f() }

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
        rootJoint.calculateWorldTransforms(Matrix4f())
        for (i in 0 until jointCount) {
            val joint = joints[i]
            if (joint != null) {
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
        return Skeleton(rootJoint.copy(), jointCount)
    }
}
