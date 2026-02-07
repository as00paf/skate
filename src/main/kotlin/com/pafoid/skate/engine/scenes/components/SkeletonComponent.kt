package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.animation.SkeletonMath
import com.pafoid.skate.engine.animation.SkeletonPose
import org.joml.Matrix4f

class SkeletonComponent(
    val pose: SkeletonPose? = null
) : Component() {
    private val matrixPalette = pose?.let { Array<Matrix4f>(it.skeleton.boneCount) { Matrix4f() } } ?: emptyArray()

    init {
        // Compute initial pose
        if (pose != null) {
            SkeletonMath.buildSkinMatrices(pose, matrixPalette)
        }
    }

    fun getMatrixPalette(): Array<Matrix4f> = matrixPalette
}