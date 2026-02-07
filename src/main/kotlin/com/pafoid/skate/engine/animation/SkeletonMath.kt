package com.pafoid.skate.engine.animation

import org.joml.Matrix4f

object SkeletonMath {
    private val IDENTITY_MATRIX = Matrix4f()

    fun computeGlobalTransforms(rootBone: Bone, localTransforms: Array<Matrix4f>, globalTransforms: Array<Matrix4f>) {
        computeGlobalTransformsRecursive(rootBone, IDENTITY_MATRIX, localTransforms, globalTransforms)
    }

    private fun computeGlobalTransformsRecursive(
        bone: Bone,
        parentTransform: Matrix4f,
        localTransforms: Array<Matrix4f>,
        globalTransforms: Array<Matrix4f>
    ) {
        val boneLocalTransform = if (bone.index >= 0 && bone.index < localTransforms.size) {
            localTransforms[bone.index]
        } else {
            bone.localTransform
        }

        // Compute world transform directly into bone.worldTransform (reusing object)
        parentTransform.mul(boneLocalTransform, bone.worldTransform)

        // Update globalTransforms array if the bone has a valid index
        if (bone.index >= 0 && bone.index < globalTransforms.size) {
            globalTransforms[bone.index].set(bone.worldTransform)
        }

        for (child in bone.children) {
            // Pass the updated bone.worldTransform as parent for children
            computeGlobalTransformsRecursive(child, bone.worldTransform, localTransforms, globalTransforms)
        }
    }

    fun buildSkinMatrices(pose: SkeletonPose, outPalette: Array<Matrix4f>) {
        val skeleton = pose.skeleton
        val boneCount = minOf(skeleton.boneCount, outPalette.size)

        // Compute global transforms from local transforms
        computeGlobalTransforms(skeleton.rootBone, pose.localTransforms, pose.globalTransforms)

        // Build the skin matrices
        for (i in 0 until boneCount) {
            val bone = skeleton.bones[i]
            if (bone != null) {
                // Skin matrix = globalTransform * inverseBindMatrix
                pose.globalTransforms[i].mul(bone.inverseBindMatrix, outPalette[i])
            } else {
                outPalette[i].identity()
            }
        }
    }

    fun resetToBindPose(pose: SkeletonPose) {
        for (i in 0 until pose.skeleton.boneCount) {
            val bone = pose.skeleton.bones[i]
            if (bone != null) {
                pose.localTransforms[i].set(bone.bindLocalTransform)
            }
        }
    }
}