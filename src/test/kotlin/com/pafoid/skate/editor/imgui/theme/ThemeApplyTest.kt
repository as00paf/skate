package com.pafoid.skate.editor.imgui.theme

import com.pafoid.skate.editor.imgui.ImGuiStyleManager
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
