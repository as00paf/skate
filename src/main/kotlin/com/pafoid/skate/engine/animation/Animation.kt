package com.pafoid.skate.engine.animation

class Animation(
    val name: String,
    val channels: List<AnimationChannel>,
    val duration: Float
) {
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
}
