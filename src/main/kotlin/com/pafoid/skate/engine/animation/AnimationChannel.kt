package com.pafoid.skate.engine.animation

enum class AnimationPath {
    TRANSLATION,
    ROTATION,
    SCALE
}

class AnimationChannel(
    val sampler: AnimationSampler,
    val targetNodeId: Int,
    val path: AnimationPath
)
