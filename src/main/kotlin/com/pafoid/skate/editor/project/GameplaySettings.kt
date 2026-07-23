package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.input.InputMappings
import kotlinx.serialization.Serializable

@Serializable
data class GameplaySettings(
    val inputMappings: InputMappings = InputMappings()
)