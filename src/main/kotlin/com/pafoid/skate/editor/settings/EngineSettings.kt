package com.pafoid.skate.editor.settings

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
