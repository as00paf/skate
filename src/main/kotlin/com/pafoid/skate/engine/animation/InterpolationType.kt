package com.pafoid.skate.engine.animation

import kotlinx.serialization.Serializable

@Serializable
enum class InterpolationType {
    STEP,
    LINEAR,
    CUBIC_SPLINE
}