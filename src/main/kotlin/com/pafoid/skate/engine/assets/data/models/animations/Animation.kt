package com.pafoid.skate.engine.assets.data.models.animations

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Represents a single animation clip containing multiple channels (TRS tracks).
 */
@Serializable
class Animation(
    var name: String,
    @Transient val channels: List<AnimationChannel> = emptyList(),
    var duration: Float,
    var path: String
) {
    /**
     * Updates the [skeleton] based on the specified [time].
     */
    fun update(time: Float, skeleton: Skeleton) {
        val loopTime = time % duration

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

}

