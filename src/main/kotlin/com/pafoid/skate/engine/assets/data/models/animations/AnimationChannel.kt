package com.pafoid.skate.engine.assets.data.models.animations

import kotlinx.serialization.Serializable

@Serializable
data class AnimationChannel(
    val sampler: AnimationSampler,
    val targetNodeName: String,
    val path: AnimationPath,
)
