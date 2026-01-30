package com.pafoid.skate.engine.animation

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
        val tempVec3 = org.joml.Vector3f()
        val tempQuat = org.joml.Quaternionf()
        
        for (channel in channels) {
            val joint = skeleton.getJointByName(channel.targetNodeName) ?: continue
            
            when (channel.path) {
                AnimationPath.TRANSLATION -> {
                    channel.sampler.sampleVector3f(loopTime, tempVec3)
                    joint.localTransform.translation(tempVec3)
                }
                AnimationPath.ROTATION -> {
                    channel.sampler.sampleQuaternionf(loopTime, tempQuat)
                    joint.localTransform.rotation(tempQuat)
                }
                AnimationPath.SCALE -> {
                    channel.sampler.sampleVector3f(loopTime, tempVec3)
                    joint.localTransform.scale(tempVec3)
                }
            }
        }
    }

    /**
     * Updates the [skeleton] by blending this animation with its current state.
     * 
     * @param alpha Interpolation factor (0.0 = current state, 1.0 = this animation).
     */
    fun updateBlended(time: Float, skeleton: Skeleton, alpha: Float) {
        val loopTime = time % duration
        val tempVec3 = org.joml.Vector3f()
        val targetVec3 = org.joml.Vector3f()
        val tempQuat = org.joml.Quaternionf()
        val targetQuat = org.joml.Quaternionf()
        
        for (channel in channels) {
            val joint = skeleton.getJointByName(channel.targetNodeName) ?: continue
            
            when (channel.path) {
                AnimationPath.TRANSLATION -> {
                    joint.localTransform.getTranslation(tempVec3)
                    channel.sampler.sampleVector3f(loopTime, targetVec3)
                    tempVec3.lerp(targetVec3, alpha)
                    joint.localTransform.translation(tempVec3)
                }
                AnimationPath.ROTATION -> {
                    joint.localTransform.getUnnormalizedRotation(tempQuat)
                    channel.sampler.sampleQuaternionf(loopTime, targetQuat)
                    tempQuat.slerp(targetQuat, alpha)
                    joint.localTransform.rotation(tempQuat)
                }
                AnimationPath.SCALE -> {
                    joint.localTransform.getScale(tempVec3)
                    channel.sampler.sampleVector3f(loopTime, targetVec3)
                    tempVec3.lerp(targetVec3, alpha)
                    joint.localTransform.scale(tempVec3)
                }
            }
        }
    }
}
