package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.imgui.theme.EditorTheme
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempFile

/** EDT-07: the chosen theme is saved in the editor settings and read back on the next start. */
class ThemeSettingTest {
    private val file: File = createTempFile("editor", ".config").toFile().also { it.delete() }
    private val logger = LoggerService().apply { consoleSink = { _, _ -> } }

    private fun manager() = EditorSettingsManager(Serializer(), EventSystem(), logger, mockk<StringManager>(relaxed = true), file)

    @AfterEach
    fun cleanup() {
        file.delete()
    }

    @Test
    fun ThemeChoice_Persists() {
        val first = manager().apply { loadSettings() }
        assertEquals(EditorTheme.DEFAULT_ID, first.editorSettings.theme)
        first.updateEditorSettings(theme = "high_contrast")

        val restarted = manager().apply { loadSettings() }
        assertEquals("high_contrast", restarted.editorSettings.theme)
    }
}
