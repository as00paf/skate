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
    }
}