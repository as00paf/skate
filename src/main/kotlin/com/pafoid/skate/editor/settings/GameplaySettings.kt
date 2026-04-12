package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

@Serializable
data class GameplaySettings(
    val physicsFPS: Int = 60,
    val gravity: Float = -9.81f,
    val timeScale: Float = 1.0f
)