package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.utils.SkeletonMath
import org.joml.Matrix4f

class SkeletonComponent(
    val pose: SkeletonPose
) : Component() {

    private val matrixPalette = pose?.let { Array(it.skeleton.boneCount) { Matrix4f() } } ?: emptyArray()

    init {
        // Compute initial pose
        SkeletonMath.buildSkinMatrices(pose, matrixPalette)

    }


    fun getMatrixPalette(): Array<Matrix4f> = matrixPalette
}