package com.pafoid.skate.engine.assets.data.models.animations

import kotlinx.serialization.Serializable

@Serializable
enum class InterpolationType {
    STEP,
    LINEAR,
    CUBIC_SPLINE
}