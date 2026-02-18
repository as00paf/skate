package com.pafoid.skate.engine.utils

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import org.joml.Matrix4f

object SkeletonMath {
    /**
     * Entry point for computing global (world-space) transforms for a bone hierarchy.
     * It starts a recursive traversal from the root bone using an identity matrix as the initial parent transform.
     *
     * @param rootBone The root of the skeleton hierarchy.
     * @param localTransforms An array of local-space matrices for each bone index.
     * @param globalTransforms An array where the computed world-space matrices will be stored.
     */
    fun computeGlobalTransforms(rootBone: Bone, localTransforms: Array<Matrix4f>, globalTransforms: Array<Matrix4f>) {
        computeGlobalTransformsRecursive(rootBone, Matrix4f(), localTransforms, globalTransforms)
    }

    /**
     * Recursively computes world-space matrices for a bone and all its children.
     * The world-space transform of a bone is calculated by multiplying its parent's world-space transform
     * with its own local-space transform (World = ParentWorld * Local).
     *
     * This method also updates the [Bone.worldTransform] property for internal engine use and
     * populates the [globalTransforms] palette used for skinning.
     *
     * @param bone The current bone being processed.
     * @param parentTransform The computed world-space matrix of the parent bone.
     * @param localTransforms The source array of local-space transforms (current pose).
     * @param globalTransforms The target array for computed world-space transforms.
     */
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