package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.utils.SkeletonMath
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f

@Serializable
data class SkeletonComponent(
    val pose: SkeletonPose
) : Component() {
    @Transient
    var selectedBone: Bone? = null

    @Transient
    private val matrixPalette = Array(pose.skeleton.boneCount) { Matrix4f() }

    init {
        // Compute initial pose
        SkeletonMath.buildSkinMatrices(pose, matrixPalette)
    }

    override fun reset() {
        super.reset()
        matrixPalette.forEach { it.set(Matrix4f()) }
        pose.reset()
        SkeletonMath.buildSkinMatrices(pose, matrixPalette)
    }

    fun getMatrixPalette(): Array<Matrix4f> = matrixPalette
}