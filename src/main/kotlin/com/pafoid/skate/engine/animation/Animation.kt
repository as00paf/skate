package com.pafoid.skate.engine.animation

import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Represents a single animation clip containing multiple channels (TRS tracks).
 */
class Animation(
    val name: String,
    val channels: List<AnimationChannel>,
    val duration: Float
) {
    /**
     * Updates the [skeleton] based on the specified [time].
     * Individual channels sample their data and apply it to the corresponding joints.
     */
    fun update(time: Float, skeleton: Skeleton) {
        val loopTime = time % duration
        val pos = Vector3f()
        val rot = Quaternionf()
        val scale = Vector3f()
        
        for (channel in channels) {
            val joint = skeleton.getJointByName(channel.targetNodeName) ?: continue
            
            // Read current state
            joint.localTransform.getTranslation(pos)
            joint.localTransform.getUnnormalizedRotation(rot)
            joint.localTransform.getScale(scale)
            
            when (channel.path) {
                AnimationPath.TRANSLATION -> {
                    channel.sampler.sampleVector3f(loopTime, pos)
                }
                AnimationPath.ROTATION -> {
                    channel.sampler.sampleQuaternionf(loopTime, rot)
                }
                AnimationPath.SCALE -> {
                    channel.sampler.sampleVector3f(loopTime, scale)
                }
            }
            
            // Recompose matrix
            joint.localTransform.translationRotateScale(pos, rot, scale)
        }
    }

    /**
     * Updates the [skeleton] by blending this animation with its current state.
     * 
     * @param alpha Interpolation factor (0.0 = current state, 1.0 = this animation).
     */
    fun updateBlended(time: Float, skeleton: Skeleton, alpha: Float) {
        val loopTime = time % duration
        val pos = Vector3f()
        val rot = Quaternionf()
        val scale = Vector3f()
        val targetVec3 = Vector3f()
        val targetQuat = Quaternionf()
        
        for (channel in channels) {
            val joint = skeleton.getJointByName(channel.targetNodeName) ?: continue
            
            // Read current state
            joint.localTransform.getTranslation(pos)
            joint.localTransform.getUnnormalizedRotation(rot)
            joint.localTransform.getScale(scale)
            
            when (channel.path) {
                AnimationPath.TRANSLATION -> {
                    channel.sampler.sampleVector3f(loopTime, targetVec3)
                    pos.lerp(targetVec3, alpha)
                }
                AnimationPath.ROTATION -> {
                    channel.sampler.sampleQuaternionf(loopTime, targetQuat)
                    rot.slerp(targetQuat, alpha)
                }
                AnimationPath.SCALE -> {
                    channel.sampler.sampleVector3f(loopTime, targetVec3)
                    scale.lerp(targetVec3, alpha)
                }
            }
            
            // Recompose matrix
            joint.localTransform.translationRotateScale(pos, rot, scale)
        }
    }
}
