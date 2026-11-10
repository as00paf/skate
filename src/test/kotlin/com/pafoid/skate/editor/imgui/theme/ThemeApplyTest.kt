package com.pafoid.skate.editor.imgui.theme

import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.settings.AppearanceSettings
import com.pafoid.skate.editor.settings.Density
import com.pafoid.skate.engine.assets.Assets
import imgui.ImGui
import imgui.ImVec4
import imgui.flag.ImGuiCol
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-07: switching theme changes the live ImGui style on the next sync, with no restart. */
class ThemeApplyTest {

    @BeforeEach
    fun setup() {
        ImGui.createContext()
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
    }

    @AfterEach
    fun teardown() {
        ImGuiStyleManager.preview = null
        ImGuiStyleManager.previewAppearance = null
        ImGuiStyleManager.previewUiScale = null
        ImGuiStyleManager.appearance = { AppearanceSettings.THEME_ONLY }
        ImGuiStyleManager.uiScale = { 1f }
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        ImGui.destroyContext()
    }

    private fun windowBg(): ImVec4 = ImVec4().also { ImGui.getStyle().getColor(ImGuiCol.WindowBg, it) }

    private fun assertBackground(themeId: String) {
        val expected = ImGuiStyleManager.library.resolve(themeId).style.theme.background
        val actual = windowBg()
        assertEquals(expected.x, actual.x, 1e-3f, "WindowBg of $themeId")
        assertEquals(expected.y, actual.y, 1e-3f)
        assertEquals(expected.z, actual.z, 1e-3f)
    }

    @Test
    fun SavedThemeSwitch_AppliesLive() {
        ImGuiStyleManager.sync("dark")
        assertBackground("dark")
        assertEquals(1f, ImGui.getIO().fontGlobalScale)

        ImGuiStyleManager.sync("light")
        assertEquals("light", ImGuiStyleManager.appliedId)
        assertBackground("light")
        assertEquals(1f, ImGui.getStyle().frameBorderSize)
    }

    @Test
    fun Preview_AppliesThenRevertsOnCancel() {
        ImGuiStyleManager.sync("dark")
        ImGuiStyleManager.preview = "high_contrast"
        ImGuiStyleManager.sync("dark")
        assertBackground("high_contrast")
        assertEquals(1.15f, ImGui.getIO().fontGlobalScale, 1e-4f)
        assertEquals(0f, ImGui.getStyle().frameRounding)

        ImGuiStyleManager.preview = null // Cancel
        ImGuiStyleManager.sync("dark")
        assertBackground("dark")
        assertEquals(1f, ImGui.getIO().fontGlobalScale)
    }

    @Test
    fun Appearance_AppliesLiveOverTheTheme() {
        // EDT-76: each option changes the live style on the next sync, with the theme unchanged.
        var saved = AppearanceSettings()
        ImGuiStyleManager.appearance = { saved }
        ImGuiStyleManager.sync("dark")
        val comfortable = Density.COMFORTABLE.spacing!!
        assertEquals(comfortable.framePaddingX, ImGui.getStyle().framePaddingX)
        assertEquals(comfortable.itemSpacingY, ImGui.getStyle().itemSpacingY)
        assertEquals(comfortable.windowPaddingX, ImGui.getStyle().windowPaddingX)

        saved = saved.withDensity(Density.SPACIOUS)
        ImGuiStyleManager.sync("dark")
        assertEquals(Density.SPACIOUS.spacing!!.framePaddingY, ImGui.getStyle().framePaddingY)

        saved = saved.copy(rounding = 1f)
        ImGuiStyleManager.sync("dark")
        assertEquals(1f, ImGui.getStyle().frameRounding)
        assertEquals(1f, ImGui.getStyle().windowRounding)

        saved = saved.withCustom(Density.SPACIOUS.spacing!!) { it.copy(itemSpacingX = 17f) }
        ImGuiStyleManager.sync("dark")
        assertEquals(17f, ImGui.getStyle().itemSpacingX)
    }

    @Test
    fun AppearanceAndFontSizePreview_ApplyThenRevert() {
        ImGuiStyleManager.appearance = { AppearanceSettings() }
        ImGuiStyleManager.sync("dark")
        ImGuiStyleManager.previewAppearance = AppearanceSettings().withDensity(Density.COMPACT)
        ImGuiStyleManager.previewUiScale = 1.5f
        ImGuiStyleManager.sync("dark")
        assertEquals(Density.COMPACT.spacing!!.framePaddingX, ImGui.getStyle().framePaddingX)
        assertEquals(1.5f, ImGui.getIO().fontGlobalScale, 1e-4f)

        ImGuiStyleManager.previewAppearance = null // Cancel
        ImGuiStyleManager.previewUiScale = null
        ImGuiStyleManager.sync("dark")
        assertEquals(Density.COMFORTABLE.spacing!!.framePaddingX, ImGui.getStyle().framePaddingX)
        assertEquals(1f, ImGui.getIO().fontGlobalScale, 1e-4f)
    }

    @Test
    fun Spacing_ReachesImGui() {
        // EDT-72: imgui-java's ImVec2 style getters return copies; the theme's padding must use the setters.
        ImGuiStyleManager.sync("high_contrast")
        val s = ImGuiStyleManager.library.resolve("high_contrast").style
        val style = ImGui.getStyle()
        assertEquals(s.framePadding.x, style.framePaddingX); assertEquals(s.framePadding.y, style.framePaddingY)
        assertEquals(s.windowPadding.x, style.windowPaddingX); assertEquals(s.windowPadding.y, style.windowPaddingY)
        assertEquals(s.itemSpacing.x, style.itemSpacingX); assertEquals(s.itemSpacing.y, style.itemSpacingY)
        assertEquals(s.itemInnerSpacing.x, style.itemInnerSpacingX)
        assertEquals(s.touchExtraPadding.y, style.touchExtraPaddingY)
    }

    @Test
    fun TablesDockingAndNavHighlight_FollowTheTheme() {
        ImGuiStyleManager.sync("light")
        val t = ImGuiStyleManager.library.resolve("light").style.theme
        fun color(col: Int) = ImVec4().also { ImGui.getStyle().getColor(col, it) }
        fun same(expected: org.joml.Vector4f, actual: ImVec4, what: String) {
            assertEquals(expected.x, actual.x, 1e-3f, what); assertEquals(expected.y, actual.y, 1e-3f, what); assertEquals(expected.z, actual.z, 1e-3f, what)
        }
        same(t.header, color(ImGuiCol.TableHeaderBg), "TableHeaderBg")
        same(t.border, color(ImGuiCol.TableBorderStrong), "TableBorderStrong")
        same(t.background, color(ImGuiCol.DockingEmptyBg), "DockingEmptyBg")
        same(t.navHighlight, color(ImGuiCol.NavHighlight), "NavHighlight")
    }
}
