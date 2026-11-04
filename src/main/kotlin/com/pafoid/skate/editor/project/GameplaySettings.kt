package com.pafoid.skate.editor.project

import com.pafoid.skate.editor.settings.AudioSettings
import com.pafoid.skate.engine.input.InputMappings
import kotlinx.serialization.Serializable

@Serializable
data class GameplaySettings(
    val inputMappings: InputMappings = InputMappings(),
    val audio: AudioSettings = AudioSettings(),
)