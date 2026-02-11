package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.utils.BoneNameMapper
import kotlinx.serialization.Serializable
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Represents a single animation clip containing multiple channels (TRS tracks).
 */
@Serializable
class Animation(
    val name: String,
    val channels: List<AnimationChannel>,
    val duration: Float,
) {
    /**
     * Updates the [skeleton] based on the specified [time].
     */
    fun update(time: Float, skeleton: Skeleton) {
        val loopTime = time % duration

        // 1. Reset affected bones to their Bind Pose (Model Space) - ONCE PER BONE
        val affectedBones = mutableSetOf<Bone>()
        channels.forEach { channel ->
            val bone = skeleton.getBoneByName(channel.targetNodeName)
            if (bone != null && affectedBones.add(bone)) {
                bone.localTransform.set(bone.bindLocalTransform)

                if (bone.name.equals("Hips", ignoreCase = true)) {
                    //This was a test, not sure if needed anymore
                    //bone.localTransform.identity()
                }
            }
        }

        // 2. Apply Animation Channels
        // Group channels by bone to process all channels for each bone together
        val channelsByBone = channels.groupBy { it.targetNodeName }

        for ((boneName, boneChannels) in channelsByBone) {
            val bone = skeleton.getBoneByName(boneName) ?: continue

            // Start with the bind pose transform
            val pos = Vector3f()
            val rot = Quaternionf()
            val scale = Vector3f()  // Default to identity scale

            // Read current state (though it will be overwritten by bindLocalTransform)
            bone.localTransform.getTranslation(pos)
            bone.localTransform.getUnnormalizedRotation(rot)
            bone.localTransform.getScale(scale)  // Get the current scale from bind pose

            // Process each channel for this bone
            for (channel in boneChannels) {
                when (channel.path) {
                    AnimationPath.TRANSLATION -> channel.sampler.sampleVector3f(loopTime, pos)
                    AnimationPath.ROTATION -> channel.sampler.sampleQuaternionf(loopTime, rot)
                    AnimationPath.SCALE -> channel.sampler.sampleVector3f(loopTime, scale)
                }
            }

            val animatedLocal = Matrix4f()
            animatedLocal.translationRotateScale(pos, rot, scale)

            bone.localTransform
                .set(bone.bindLocalTransform)
                .mul(animatedLocal)
        }
    }

    /**
     * Updates the [skeleton] by blending.
     */
    fun updateBlended(time: Float, skeleton: Skeleton, alpha: Float) {
        val loopTime = time % duration

        // Map to store the target animation transforms
        val targetTransforms = mutableMapOf<String, Matrix4f>()

        val pos = Vector3f()
        val rot = Quaternionf()
        val scale = Vector3f()

        // Calculate Target State for all affected bones
        for (channel in channels) {
            val mappedName = BoneNameMapper.map(channel.targetNodeName)
            val bone = skeleton.getBoneByName(mappedName) ?: continue

            // Start with Bind Pose
            val targetMat = targetTransforms.getOrPut(bone.name) { Matrix4f(bone.bindLocalTransform) }

            targetMat.getTranslation(pos)
            targetMat.getUnnormalizedRotation(rot)
            targetMat.getScale(scale)

            when (channel.path) {
                AnimationPath.TRANSLATION -> channel.sampler.sampleVector3f(loopTime, pos)
                AnimationPath.ROTATION -> channel.sampler.sampleQuaternionf(loopTime, rot)
                AnimationPath.SCALE -> channel.sampler.sampleVector3f(loopTime, scale)
            }

            targetMat.translationRotateScale(pos, rot, Vector3f())
        }

        // Blend current state with target state
        targetTransforms.forEach { (name, targetMat) ->
            val bone = skeleton.getBoneByName(name) ?: return@forEach

            // Interpolate
            val currentMat = bone.localTransform

            val t1 = Vector3f(); val r1 = Quaternionf(); val s1 = Vector3f()
            currentMat.getTranslation(t1)
            currentMat.getUnnormalizedRotation(r1)
            currentMat.getScale(s1)

            val t2 = Vector3f(); val r2 = Quaternionf(); val s2 = Vector3f()
            targetMat.getTranslation(t2)
            targetMat.getUnnormalizedRotation(r2)
            targetMat.getScale(s2)

            t1.lerp(t2, alpha)
            r1.slerp(r2, alpha)
            s1.lerp(s2, alpha)

            bone.localTransform.translationRotateScale(t1, r1, s1)
        }
    }

}

