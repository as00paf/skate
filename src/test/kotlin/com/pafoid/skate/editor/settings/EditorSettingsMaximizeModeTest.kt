package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.imgui.MaximizeMode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** EDT-88 Q1: Maximize fills the editor by default; the choice is saved with the editor settings. */
class EditorSettingsMaximizeModeTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun DefaultsToFullEditor_EvenForSettingsSavedBefore() {
        assertEquals(MaximizeMode.FULL_EDITOR, EditorSettings().maximizeMode)
        assertEquals(MaximizeMode.FULL_EDITOR, json.decodeFromString<EditorSettings>("{}").maximizeMode)
    }

    @Test
    fun RoundTrips() {
        val saved = json.encodeToString(EditorSettings.serializer(), EditorSettings(maximizeMode = MaximizeMode.OVER_VIEWPORT))
        assertEquals(MaximizeMode.OVER_VIEWPORT, json.decodeFromString<EditorSettings>(saved).maximizeMode)
    }
}
