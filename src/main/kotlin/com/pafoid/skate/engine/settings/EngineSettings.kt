package com.pafoid.skate.engine.settings

import com.pafoid.skate.engine.input.EditorInputMappings
import com.pafoid.skate.engine.utils.UnitSystem
import kotlinx.serialization.Serializable

/**
 * Engine-wide settings that apply globally across all projects.
 * These settings are stored in the user's settings directory.
 *
 * @property editor Editor UI settings (language, theme, overlays)
 * @property autoSave Auto-save configuration
 */
@Serializable
data class EngineSettings(
    val editor: EditorSettings = EditorSettings(),
    val autoSave: AutoSaveSettings = AutoSaveSettings()
) {
    fun validate(): EngineSettings {
        return copy(
            editor = editor.validate()
        )
    }
}

/**
 * Editor UI settings.
 */
@Serializable
data class EditorSettings(
    val language: String = "en",
    val theme: String = "Islands Dark",
    val showGamepadOverlay: Boolean = true,
    val gamepadOverlaySize: Float = 0.225f,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val editorInputMappings: EditorInputMappings = EditorInputMappings()
) {
    fun validate(): EditorSettings {
        return copy(
            gamepadOverlaySize = gamepadOverlaySize.coerceIn(0.1f, 0.5f)
        )
    }
}

/**
 * Auto-save configuration settings.
 */
@Serializable
data class AutoSaveSettings(
    val enabled: Boolean = true,
    val intervalMinutes: Int = 5
) {
    fun validate(): AutoSaveSettings {
        return copy(
            intervalMinutes = intervalMinutes.coerceIn(1, 60)
        )
    }
}
