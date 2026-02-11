package com.pafoid.skate.engine.animation

import kotlinx.serialization.Serializable

@Serializable
data class AnimationChannel(
    val sampler: AnimationSampler,
    val targetNodeName: String,
    val path: AnimationPath,
)
