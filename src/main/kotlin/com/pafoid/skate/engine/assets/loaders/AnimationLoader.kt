package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.assets.BoneNameMapper
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.AnimationChannel
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.AnimationSampler
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.assimp.AIAnimation
import org.lwjgl.assimp.AINodeAnim
import org.lwjgl.assimp.Assimp.aiGetErrorString
import org.lwjgl.assimp.Assimp.aiImportFile
import org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices
import org.lwjgl.assimp.Assimp.aiProcess_LimitBoneWeights
import org.lwjgl.assimp.Assimp.aiProcess_Triangulate
import org.lwjgl.assimp.Assimp.aiReleaseImport
import java.math.BigDecimal
import java.math.RoundingMode

class AnimationLoader {

    fun loadAnimations(filePath: String, skeleton: Skeleton): List<Animation> {
        val scene = aiImportFile(
            filePath,
            aiProcess_Triangulate or aiProcess_JoinIdenticalVertices or aiProcess_LimitBoneWeights
        )
            ?: throw RuntimeException("Error loading animations: " + aiGetErrorString())

        val animations = mutableListOf<Animation>()
        val sceneAnimations = scene.mAnimations() ?: throw Error("No animation found in animation file")

        val aiAnimInfo =
            getAnimInfo(AIAnimation.create(sceneAnimations.get(0)), skeleton)

        for (i in 0 until scene.mNumAnimations()) {
            val aiAnim = AIAnimation.create(sceneAnimations.get(i))
            animations.add(processAnimation(filePath, aiAnim, aiAnimInfo))
        }

        aiReleaseImport(scene)
        return animations
    }

    private fun getAnimInfo(aiAnim: AIAnimation, skeleton: Skeleton): AiAnimationInfo {
        val rootNodeName = skeleton.rootBone.children.first().name
        var isRoot = false
        var i = 0
        var aiChannel: AINodeAnim? = null
        while (!isRoot && i < aiAnim.mNumChannels()) {
            val animationChannels = aiAnim.mChannels() ?: continue
            aiChannel = AINodeAnim.create(animationChannels.get(1))
            val originalName = aiChannel.mNodeName().dataString()
            val nodeName = BoneNameMapper.map(originalName)
            isRoot = nodeName.equals(rootNodeName, ignoreCase = true) || nodeName.equals("Hips", ignoreCase = true)

            i++
        }

        if (aiChannel == null || !isRoot) throw Error("Could not get anim info")

        val firstPosY = aiChannel.mPositionKeys()?.get(0)?.mValue()?.y() ?: 0f

        val hipBonePos = Vector3f()
        val hipBone = skeleton.rootBone.children.firstOrNull { it.name == "Hips" }
        hipBone?.bindLocalTransform?.getTranslation(hipBonePos)

        val finalScale = hipBonePos.y() / firstPosY
        val finalRoundedScale = BigDecimal(finalScale.toDouble())
            .setScale(2, RoundingMode.HALF_EVEN)
            .toFloat()

        return AiAnimationInfo(
            finalRoundedScale,
            firstPosY - hipBonePos.y(),
            rootNodeName,
        )
    }

    data class AiAnimationInfo(
        val scale: Float = 1.0f,
        val hipsOffset: Float = 0f,
        val rootNodeName: String = "Hips"
    )

    fun processAnimation(
        path: String,
        aiAnim: AIAnimation,
        info: AiAnimationInfo,
    ): Animation {
        val name = path.substringBefore(".fbx").replaceBeforeLast("/", "").replace("/", "").capitalize()
        val duration = aiAnim.mDuration().toFloat()
        val ticksPerSecond = if (aiAnim.mTicksPerSecond() != 0.0) aiAnim.mTicksPerSecond().toFloat() else 60f
        val durationInSeconds = duration / ticksPerSecond

        val channels = mutableListOf<AnimationChannel>()
        for (i in 0 until aiAnim.mNumChannels()) {
            val animationChannels = aiAnim.mChannels() ?: continue
            val aiChannel = AINodeAnim.create(animationChannels.get(i))
            val originalName = aiChannel.mNodeName().dataString()
            val nodeName = BoneNameMapper.map(originalName)
            val isRoot = nodeName.equals(info.rootNodeName, ignoreCase = true)

            // Translation
            // For root bones, we zero out translation to keep animations in place
            if (aiChannel.mNumPositionKeys() > 0) {
                val times = FloatArray(aiChannel.mNumPositionKeys())
                val values = FloatArray(aiChannel.mNumPositionKeys() * 3)

                for (k in 0 until aiChannel.mNumPositionKeys()) {
                    val key = aiChannel.mPositionKeys()?.get(k) ?: continue
                    times[k] = key.mTime().toFloat() / ticksPerSecond

                    values[k * 3] = if (isRoot) 0f else key.mValue().x() * info.scale
                    values[k * 3 + 1] =
                        if (isRoot) (key.mValue().y() * info.scale) - (info.hipsOffset * info.scale) else 0f
                    values[k * 3 + 2] = if (isRoot) 0f else key.mValue().z() * info.scale
                }

                channels.add(
                    AnimationChannel(
                        AnimationSampler(times, values, InterpolationType.LINEAR, 3),
                        nodeName,
                        AnimationPath.TRANSLATION
                    )
                )
            }

            // Rotation
            if (aiChannel.mNumRotationKeys() > 0) {
                val times = FloatArray(aiChannel.mNumRotationKeys())
                val values = FloatArray(aiChannel.mNumRotationKeys() * 4)
                for (k in 0 until aiChannel.mNumRotationKeys()) {
                    val keys = aiChannel.mRotationKeys() ?: continue
                    val key = keys.get(k)
                    times[k] = key.mTime().toFloat() / ticksPerSecond

                    val q = Quaternionf(key.mValue().x(), key.mValue().y(), key.mValue().z(), key.mValue().w())

                    values[k * 4] = q.x
                    values[k * 4 + 1] = q.y
                    values[k * 4 + 2] = q.z
                    values[k * 4 + 3] = q.w
                }
                val sampler = AnimationSampler(times, values, InterpolationType.LINEAR, 4)
                channels.add(
                    AnimationChannel(
                        sampler, nodeName, AnimationPath.ROTATION,
                    )
                )
            }

            // Scale
            if (aiChannel.mNumScalingKeys() > 0) {
                val times = FloatArray(aiChannel.mNumScalingKeys())
                val values = FloatArray(aiChannel.mNumScalingKeys() * 3)
                for (k in 0 until aiChannel.mNumScalingKeys()) {
                    val keys = aiChannel.mScalingKeys() ?: continue
                    val key = keys.get(k)
                    times[k] = key.mTime().toFloat() / ticksPerSecond
                    values[k * 3] = key.mValue().x()
                    values[k * 3 + 1] = key.mValue().y()
                    values[k * 3 + 2] = key.mValue().z()
                }
                val sampler = AnimationSampler(times, values, InterpolationType.LINEAR, 3)
                channels.add(AnimationChannel(sampler, nodeName, AnimationPath.SCALE))
            }
        }

        return Animation(name, channels, durationInSeconds, path)
    }

}