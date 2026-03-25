package com.pafoid.skate.editor.data

import com.pafoid.skate.engine.input.EditorInputMappings
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.utils.UnitSystem
import kotlinx.serialization.Serializable

@Serializable
data class SystemSettings(
    var gamepadOverlaySize: Float = 0.225f,
    var showGamepadOverlay: Boolean = true,
    var unitSystem: UnitSystem = UnitSystem.METRIC,
    var language: String = "en",
    var inputMappings: InputMappings = InputMappings(),
    var editorInputMappings: EditorInputMappings = EditorInputMappings(),
    var inputSettings: InputSettings = InputSettings()
)
