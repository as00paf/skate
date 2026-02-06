package com.pafoid.skate.engine.animation

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class AnimationChannel(
    val sampler: AnimationSampler,
    val targetNodeName: String,
    val path: AnimationPath
)
