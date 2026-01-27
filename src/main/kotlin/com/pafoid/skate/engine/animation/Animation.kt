package com.pafoid.skate.engine.animation

class Animation(
    val name: String,
    val channels: List<AnimationChannel>,
    val duration: Float
) {
    fun update(time: Float, nodes: Map<Int, com.pafoid.skate.engine.scenes.GameObject>) {
        val loopTime = time % duration
        val tempQuat = org.joml.Quaternionf()
        for (channel in channels) {
            val node = nodes[channel.targetNodeId] ?: continue
            when (channel.path) {
                AnimationPath.TRANSLATION -> channel.sampler.sampleVector3f(loopTime, node.transform.translation)
                AnimationPath.ROTATION -> {
                    channel.sampler.sampleQuaternionf(loopTime, tempQuat)
                    val euler = tempQuat.getEulerAnglesXYZ(org.joml.Vector3f())
                    node.transform.rotation.set(
                        Math.toDegrees(euler.x.toDouble()).toFloat(),
                        Math.toDegrees(euler.y.toDouble()).toFloat(),
                        Math.toDegrees(euler.z.toDouble()).toFloat()
                    )
                }
                AnimationPath.SCALE -> channel.sampler.sampleVector3f(loopTime, node.transform.scale)
            }
        }
    }
}
