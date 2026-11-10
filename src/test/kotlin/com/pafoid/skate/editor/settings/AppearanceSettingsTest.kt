package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.imgui.data.Style
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import io.mockk.mockk
import org.joml.Vector2f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempFile

/** EDT-76: spacing presets and overrides over the theme, saved with the editor settings. */
class AppearanceSettingsTest {
    private val file: File = createTempFile("editor", ".config").toFile().also { it.delete() }
    private val logger = LoggerService().apply { consoleSink = { _, _ -> } }

    private fun manager() = EditorSettingsManager(Serializer(), EventSystem(), logger, mockk<StringManager>(relaxed = true), file)

    @AfterEach
    fun cleanup() {
        file.delete()
    }

    private val theme = Style(rounding = 6f, windowPadding = Vector2f(8f, 8f), framePadding = Vector2f(5f, 3f), itemSpacing = Vector2f(8f, 4f))

    @Test
    fun Default_IsComfortable_WithMorePaddingThanImGuiDefaults() {
        val spacing = AppearanceSettings().spacing!!
        assertEquals(Density.COMFORTABLE.spacing, spacing)
        // ImGui's own defaults (what the editor ran with before EDT-72/76): frame (4, 3), item spacing (8, 4).
        assertTrue(spacing.framePaddingX > 4f && spacing.framePaddingY > 3f, spacing.toString())
        assertTrue(spacing.itemSpacingY > 4f, "menu items need vertical room: $spacing")
    }

    @Test
    fun Presets_GrowFromCompactToSpacious() {
        val (compact, comfortable, spacious) = listOf(Density.COMPACT, Density.COMFORTABLE, Density.SPACIOUS).map { it.spacing!! }
        listOf(Spacing::windowPaddingY, Spacing::framePaddingX, Spacing::framePaddingY, Spacing::itemSpacingY).forEach { field ->
            assertTrue(field(compact) < field(comfortable) && field(comfortable) < field(spacious), field.name)
        }
        assertNull(Density.THEME.spacing)
        assertNull(Density.CUSTOM.spacing)
    }

    @Test
    fun Apply_OverridesSpacingAndRounding_KeepsTheRestOfTheTheme() {
        val styled = AppearanceSettings.apply(theme, AppearanceSettings(density = Density.SPACIOUS, rounding = 2f))
        val s = Density.SPACIOUS.spacing!!
        assertEquals(Vector2f(s.framePaddingX, s.framePaddingY), styled.framePadding)
        assertEquals(Vector2f(s.itemSpacingX, s.itemSpacingY), styled.itemSpacing)
        assertEquals(Vector2f(s.windowPaddingX, s.windowPaddingY), styled.windowPadding)
        assertEquals(2f, styled.rounding)
        assertEquals(theme.theme, styled.theme)
        assertEquals(theme.itemInnerSpacing, styled.itemInnerSpacing)
    }

    @Test
    fun ThemeDensity_AndNoRounding_KeepTheThemeValues() {
        assertEquals(theme, AppearanceSettings.apply(theme, AppearanceSettings.THEME_ONLY))
    }

    @Test
    fun EditingAValue_SwitchesToCustom_FromWhatIsShowing() {
        val fromTheme = AppearanceSettings.THEME_ONLY.withCustom(AppearanceSettings.spacingOf(theme)) { it.copy(framePaddingY = 9f) }
        assertEquals(Density.CUSTOM, fromTheme.density)
        assertEquals(Spacing(8f, 8f, 5f, 9f, 8f, 4f), fromTheme.spacing)

        val compact = AppearanceSettings().withDensity(Density.COMPACT)
        assertEquals(Density.COMPACT.spacing, compact.custom) // tuning starts from the preset that was showing
        assertEquals(Density.COMPACT.spacing, compact.withDensity(Density.CUSTOM).spacing)
    }

    @Test
    fun Sanitized_ClampsHandEditedValues() {
        val wild = AppearanceSettings(Density.CUSTOM, Spacing(-3f, 100f, 5f, 5f, 5f, 5f), rounding = 99f).sanitized()
        assertEquals(Spacing.MIN, wild.custom.windowPaddingX)
        assertEquals(Spacing.MAX, wild.custom.windowPaddingY)
        assertEquals(AppearanceSettings.MAX_ROUNDING, wild.rounding)
    }

    @Test
    fun Appearance_Persists() {
        val first = manager().apply { loadSettings() }
        assertEquals(AppearanceSettings(), first.editorSettings.appearance)
        val chosen = AppearanceSettings(Density.CUSTOM, Spacing(3f, 4f, 11f, 6f, 9f, 7f), rounding = 0f)
        first.update { it.copy(appearance = chosen, fontSize = 1.25f) }

        val restarted = manager().apply { loadSettings() }
        assertEquals(chosen, restarted.editorSettings.appearance)
        assertEquals(1.25f, restarted.editorSettings.fontSize)
    }

    @Test
    fun OldSettingsFile_WithoutAppearance_GetsTheDefault() {
        file.writeText("""{"language":"fr","theme":"light"}""")
        val loaded = manager().apply { loadSettings() }
        assertEquals("light", loaded.editorSettings.theme)
        assertEquals(AppearanceSettings(), loaded.editorSettings.appearance)
    }
}
