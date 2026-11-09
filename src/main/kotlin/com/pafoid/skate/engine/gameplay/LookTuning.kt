package com.pafoid.skate.engine.gameplay

import kotlinx.serialization.Serializable

/** Camera look sensitivities of a character, part of its config ([LocomotionConfig.look]) so they are tunable. */
@Serializable
data class LookTuning(
    /** Degrees of camera turn per frame at full stick deflection (the look actions, -1..1). */
    val stickSensitivity: Float = 2.0f,
    /** Degrees of camera turn per cursor unit of mouse motion while the cursor is captured. */
    val mouseSensitivity: Float = 0.1f,
)
