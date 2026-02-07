package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.animation.SkeletonPose
import com.pafoid.skate.engine.animation.SkeletonMath
import org.joml.Matrix4f

class SkeletonComponent(
    val pose: SkeletonPose? = null
) : Component() {
    private val matrixPalette = pose?.let { Array<Matrix4f>(it.skeletonAsset.boneCount) { Matrix4f() } } ?: emptyArray()

    override fun update(dt: Float) {
        pose?.skeletonAsset?.update()
        // Update the matrix palette when pose changes
        if (pose != null) {
            SkeletonMath.buildSkinMatrices(pose, matrixPalette)
        }
    }

    fun getMatrixPalette(): Array<Matrix4f> = matrixPalette
}