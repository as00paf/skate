package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.assets.serialization.PoseSerializer
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.utils.SkeletonMath
import imgui.type.ImBoolean
import imgui.type.ImString
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject

@Serializable
class SkeletonComponent(
    val pose: SkeletonPose
) : Component() {

    val sceneManager: SceneManager by inject()
    val poseSerializer: PoseSerializer by inject()

    @Transient
    var selectedBone: Bone? = null
    @Transient
    val poseFileName = ImString(128)
    @Transient
    val mirrorPoseEnabled = ImBoolean(false)


    @Transient
    private val matrixPalette = Array(pose.skeleton.boneCount) { Matrix4f() }

    init {
        // Compute initial pose
        SkeletonMath.buildSkinMatrices(pose, matrixPalette)
    }

    fun getMatrixPalette(): Array<Matrix4f> = matrixPalette
}