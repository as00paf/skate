package com.pafoid.skate.game.player

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class MotionData(
    @Contextual
    val direction: Vector3f,
    val speed: Float,
    val targetYaw: Float,
    val rotationSpeed: Float,
)
