package com.pafoid.skate.game.player

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector2f

@Serializable
data class MotionData(
//TODO: move
    @Contextual
    val inputDirection: Vector2f = Vector2f(),
    val speed: Float = 0f,
    val targetYaw: Float = 0f,
    val rotationSpeed: Float = 0f,
    val isGrounded: Boolean = false,
    val wasGrounded: Boolean = true,
)
