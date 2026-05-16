package com.pafoid.skate.engine.assets.data.models.animations

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f

@Serializable
data class SkeletonPose(
    val skeleton: Skeleton
) {
    val localTransforms: Array<@Contextual Matrix4f>
    val globalTransforms: Array<@Contextual Matrix4f>

    init {
        localTransforms = Array(skeleton.boneCount) { Matrix4f() }
        globalTransforms = Array(skeleton.boneCount) { Matrix4f() }

        // Initialize with bind pose
        populateBindPose(skeleton.rootBone)
    }

    private fun populateBindPose(bone: Bone) {
        if (bone.index in 0 until skeleton.boneCount) {
            localTransforms[bone.index].set(bone.bindLocalTransform)
        }
        for (child in bone.children) {
            populateBindPose(child)
        }
    }
}