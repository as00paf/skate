package com.pafoid.skate.engine.settings

import com.pafoid.skate.engine.input.EditorInputMappings
import com.pafoid.skate.engine.utils.UnitSystem
import kotlinx.serialization.Serializable

@Serializable
data class EngineSettings(
    var display: DisplaySettings = DisplaySettings(),
    var editor: EditorSettings = EditorSettings(),
    var hardware: HardwareSettings = HardwareSettings()
)

@Serializable
data class EditorSettings(
    var language: String = "en",
    var theme: String = "default",
    var showGamepadOverlay: Boolean = true,
    var gamepadOverlaySize: Float = 0.225f,
    var unitSystem: UnitSystem = UnitSystem.METRIC,
    var editorInputMappings: EditorInputMappings = EditorInputMappings()
)

@Serializable
data class HardwareSettings(
    var leftStickDeadzone: Float = 0.15f,
    var rightStickDeadzone: Float = 0.1f,
    var triggerThreshold: Float = 0.5f,
    var mouseSensitivity: Float = 0.1f,
    var controllerSensitivity: Float = 2.0f
) {
    fun validate() {
        leftStickDeadzone = leftStickDeadzone.coerceIn(0f, 1f)
        rightStickDeadzone = rightStickDeadzone.coerceIn(0f, 1f)
        triggerThreshold = triggerThreshold.coerceIn(0f, 1f)
        mouseSensitivity = mouseSensitivity.coerceIn(0.01f, 1f)
        controllerSensitivity = controllerSensitivity.coerceIn(0.1f, 10f)
    }
}
