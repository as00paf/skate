package com.pafoid.skate.engine.animation

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f

@Serializable
class Skeleton(
    val rootBone: Bone,
    val boneCount: Int,
    val bindLocalTransforms: Array<@Contextual Matrix4f> = arrayOf(),
    val inverseBindMatrices: Array<@Contextual Matrix4f> = arrayOf(),
) {
    val bones = arrayOfNulls<Bone>(boneCount)
    private val matrixPalette = Array<@Contextual Matrix4f>(boneCount) { Matrix4f() }

    init {
        addBoneToMap(rootBone)
    }

    private fun addBoneToMap(bone: Bone) {
        if (bone.index in 0 until boneCount) {
            bones[bone.index] = bone
        }
        for (child in bone.children) {
            addBoneToMap(child)
        }
    }

    fun update() {
        rootBone.calculateWorldTransforms(Matrix4f())
        for (i in 0 until boneCount) {
            val bone = bones[i]
            if (bone != null) {
                bone.worldTransform.mul(bone.inverseBindMatrix, matrixPalette[i])
            } else {
                matrixPalette[i].identity()
            }
        }
    }

    fun getMatrixPalette(): Array<Matrix4f> = matrixPalette

    fun getBoneByName(name: String): Bone? {
        return bones.find { it?.name == name }
    }

    fun getAllBones(): List<Bone> {
        return bones.filterNotNull()
    }

    fun copy(): Skeleton {
        return Skeleton(rootBone.copy(), boneCount)
    }
}
