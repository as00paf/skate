package com.pafoid.skate.engine.animation

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f

@Serializable
data class SkeletonPose(
    val skeletonAsset: Skeleton
) {
    val localTransforms: Array<@Contextual Matrix4f>
    val globalTransforms: Array<@Contextual Matrix4f>

    init {
        localTransforms = Array(skeletonAsset.boneCount) { Matrix4f() }
        globalTransforms = Array(skeletonAsset.boneCount) { Matrix4f() }

        // Initialize with bind pose
        populateBindPose(skeletonAsset.rootBone)
    }

    private fun populateBindPose(bone: Bone) {
        if (bone.index in 0 until skeletonAsset.boneCount) {
            localTransforms[bone.index].set(bone.bindLocalTransform)
        }
        for (child in bone.children) {
            populateBindPose(child)
        }
    }
}