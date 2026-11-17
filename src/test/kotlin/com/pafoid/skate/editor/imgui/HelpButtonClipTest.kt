package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.settings.AppearanceSettings
import com.pafoid.skate.editor.settings.Density
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.type.ImString
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * EDT-72: renders real ImGui frames (editor fonts and themes, no GPU) and checks the header's `?` against the clip
 * rectangle it is drawn into: at 100 % and 150 % UI scale, with every theme, with no window padding (EDT-76 lets
 * users shrink it), at a fractional window position and with and without tools on the left.
 */
class HelpButtonClipTest {

    private val strings: StringManager = mockk(relaxed = true)

    private class Probe(
        private val strings: StringManager,
        private val modalRow: Boolean,
        private val tools: () -> Unit,
    ) : EditorWindow("window.probe") {
        override val icon: String = Icons.LIST
        override fun imgui() {
            if (beginWindow(strings)) {
                // The modal's `?` row (EditorModal.helpWindow) or the standard header.
                if (modalRow) WindowHeader.helpRow(strings) else header(strings) { tools() }
            }
            ImGui.end()
        }
    }

    @BeforeEach
    fun setup() {
        ImGui.createContext()
        val io = ImGui.getIO()
        io.iniFilename = null
        io.loadFonts(javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)!!.readAllBytes())
        io.setDisplaySize(1600f, 1000f)
        io.deltaTime = 1f / 60f
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
    }

    @AfterEach
    fun teardown() {
        ImGuiStyleManager.uiScale = { 1f }
        ImGuiStyleManager.appearance = { AppearanceSettings.THEME_ONLY }
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        ImGui.destroyContext()
    }

    private val search = ImString(64)

    private val toolSets: Map<String, () -> Unit> = mapOf(
        "none" to {},
        "search box" to { ImGui.setNextItemWidth(-1f); ImGui.inputText("##probe_search", search) },
        "small button" to { ImGui.smallButton("x##probe") },
    )

    private fun drawn(
        theme: String,
        scale: Float,
        windowPaddingY: Float?,
        tools: () -> Unit,
        modalRow: Boolean = false,
        density: Density = Density.THEME,
    ): WindowHeader.DrawnButton {
        ImGuiStyleManager.uiScale = { scale }
        ImGuiStyleManager.appearance = { AppearanceSettings(density = density) }
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.sync(theme)
        windowPaddingY?.let { ImGui.getStyle().setWindowPadding(ImGui.getStyle().windowPaddingX, it) }
        val probe = Probe(strings, modalRow, tools).also { it.isOpen.set(true) }
        repeat(2) {
            ImGui.newFrame()
            ImGui.setNextWindowPos(120.4f, 75.6f)
            ImGui.setNextWindowSize(480f, 300f)
            probe.imgui()
            ImGui.render()
        }
        return WindowHeader.lastDrawn!!
    }

    @Test
    fun HelpButton_IsNeverClipped() {
        val failures = mutableListOf<String>()
        for (theme in listOf("dark", "light", "high_contrast")) for (scale in listOf(1f, 1.5f)) for (padY in listOf(null, 0f)) {
            for ((toolsName, tools) in toolSets + ("modal ? row" to {})) {
                val (button, clip) = drawn(theme, scale, padY, tools, modalRow = toolsName == "modal ? row")
                val case = "$theme @${(scale * 100).toInt()}% padY=${padY ?: "theme"} tools=$toolsName: button=$button clip=$clip"
                if (button.minY < clip.minY + HelpButtonLayout.MIN_CLIP_CLEARANCE) failures += "top clipped: $case"
                if (button.maxY > clip.maxY) failures += "bottom clipped: $case"
                if (button.minX < clip.minX || button.maxX > clip.maxX) failures += "side clipped: $case"
                if (button.minY != kotlin.math.floor(button.minY)) failures += "not on a whole pixel: $case"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun HelpButton_IsNeverClipped_WithEveryDensity() {
        // EDT-76's spacing presets (and Custom with no window padding) at both scales.
        val failures = mutableListOf<String>()
        for (density in Density.entries) for (scale in listOf(1f, 1.5f)) for ((toolsName, tools) in toolSets) {
            val padY = if (density == Density.CUSTOM) 0f else null
            val (button, clip) = drawn("dark", scale, padY, tools, density = density)
            val case = "$density @${(scale * 100).toInt()}% tools=$toolsName: button=$button clip=$clip"
            if (button.minY < clip.minY + HelpButtonLayout.MIN_CLIP_CLEARANCE) failures += "top clipped: $case"
            if (button.maxY > clip.maxY || button.minX < clip.minX || button.maxX > clip.maxX) failures += "clipped: $case"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun HelpButton_KeepsItsSizeAtEveryScale() {
        for (scale in listOf(1f, 1.5f)) {
            val (button, _) = drawn("dark", scale, null, {})
            val font = 13f * scale
            val expected = HelpButtonLayout.size(ImGui.calcTextSize(Icons.QUESTION_CIRCLE).x, font, 5f, 3f)
            assertEquals(expected.height, button.maxY - button.minY, 0.01f, "scale $scale")
        }
    }
}
