package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.engine.utils.UnitSystem
import kotlinx.serialization.Serializable

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