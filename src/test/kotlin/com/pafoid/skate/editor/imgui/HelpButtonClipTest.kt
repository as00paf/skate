package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.settings.AppearanceSettings
import com.pafoid.skate.editor.settings.Density
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
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

    /**
     * [titleBar]: a floating window whose Maximize and `?` sit in its title bar (EDT-101); otherwise a window without a
     * title bar, standing for a docked one, whose buttons share the tools row.
     */
    private class Probe(
        private val strings: StringManager,
        private val titleBar: Boolean,
        private val tools: () -> Unit,
    ) : EditorWindow("window.console") {
        override val icon: String = Icons.LIST
        override fun imgui() {
            if (beginWindow(strings, if (titleBar) 0 else ImGuiWindowFlags.NoTitleBar)) header(strings) { tools() }
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
        titleBar: Boolean = false,
        density: Density = Density.THEME,
    ): WindowHeader.DrawnButton {
        ImGuiStyleManager.uiScale = { scale }
        ImGuiStyleManager.appearance = { AppearanceSettings(density = density) }
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.sync(theme)
        windowPaddingY?.let { ImGui.getStyle().setWindowPadding(ImGui.getStyle().windowPaddingX, it) }
        val probe = Probe(strings, titleBar, tools).also { it.isOpen.set(true) }
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
            for ((toolsName, tools) in toolSets + ("title bar" to {})) {
                val (button, clip) = drawn(theme, scale, padY, tools, titleBar = toolsName == "title bar")
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
    fun MaximizeButton_IsNeverClipped_AndSitsLeftOfHelp() {
        // EDT-88: the header reserves both buttons at every density, scale and theme.
        val failures = mutableListOf<String>()
        for (theme in listOf("dark", "light", "high_contrast")) for (density in Density.entries) for (scale in listOf(1f, 1.5f)) {
            for ((toolsName, tools) in toolSets) {
                val padY = if (density == Density.CUSTOM) 0f else null
                val (help, _) = drawn(theme, scale, padY, tools, density = density)
                val (button, clip) = WindowHeader.lastDrawnMaximize!!
                val case = "$theme $density @${(scale * 100).toInt()}% tools=$toolsName: button=$button help=$help clip=$clip"
                if (button.minY < clip.minY + HelpButtonLayout.MIN_CLIP_CLEARANCE) failures += "top clipped: $case"
                if (button.maxY > clip.maxY || button.minX < clip.minX || button.maxX > clip.maxX) failures += "clipped: $case"
                if (button.maxX > help.minX) failures += "overlaps the ?: $case"
                if (button.minY != help.minY) failures += "not on the ?'s row: $case"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun GroupWidth_IsButtonsPlusSpacing() {
        assertEquals(20f, HelpButtonLayout.groupWidth(20f, 1, 4f))
        assertEquals(44f, HelpButtonLayout.groupWidth(20f, 2, 4f))
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

    @Test
    fun TitleBar_ButtonsSitInTheTitleBar_LeftOfTheClose_AtEveryDensityAndScale() {
        // EDT-101: floating windows and dialogs carry Maximize and `?` in their title bar, never on a row of their own.
        val failures = mutableListOf<String>()
        for (theme in listOf("dark", "light", "high_contrast")) for (density in Density.entries) for (scale in listOf(1f, 1.5f)) {
            val (help, bar) = drawn(theme, scale, null, {}, titleBar = true, density = density)
            val (maximize, _) = WindowHeader.lastDrawnMaximize!!
            val case = "$theme $density @${(scale * 100).toInt()}%: help=$help maximize=$maximize bar=$bar"
            // The close button is one font size wide (as are ours) at the bar's right end minus the frame padding.
            val closeLeft = bar.maxX - ImGui.getStyle().framePaddingX - (help.maxY - help.minY)
            if (help.minY < bar.minY || help.maxY > bar.maxY) failures += "? outside the title bar: $case"
            if (help.maxX > closeLeft) failures += "? over the close button: $case"
            if (maximize.maxX > help.minX) failures += "maximize overlaps ?: $case"
            if (maximize.minY != help.minY) failures += "not side by side: $case"
            if (help.minX != kotlin.math.floor(help.minX) || help.minY != kotlin.math.floor(help.minY)) failures += "not on whole pixels: $case"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun TitleBar_HelpIsClickable_AndDoesNotMoveTheWindow() {
        var opened = 0
        val before = com.pafoid.skate.editor.help.HelpRouter.open
        com.pafoid.skate.editor.help.HelpRouter.open = { opened++ }
        try {
            ImGuiStyleManager.sync("dark")
            val probe = Probe(strings, titleBar = true) {}.also { it.isOpen.set(true) }
            fun frame() {
                ImGui.newFrame()
                ImGui.setNextWindowPos(120f, 75f, imgui.flag.ImGuiCond.FirstUseEver)
                ImGui.setNextWindowSize(480f, 300f, imgui.flag.ImGuiCond.FirstUseEver)
                probe.imgui()
                ImGui.render()
            }
            repeat(2) { frame() }
            val (help, _) = WindowHeader.lastDrawn!!
            val io = ImGui.getIO()
            io.addMousePosEvent((help.minX + help.maxX) / 2f, (help.minY + help.maxY) / 2f)
            frame()
            io.addMouseButtonEvent(0, true)
            frame()
            io.addMouseButtonEvent(0, false)
            frame()
            assertEquals(1, opened, "the ? opened the help")
            assertEquals(help, WindowHeader.lastDrawn!!.button, "the window did not move")
        } finally {
            com.pafoid.skate.editor.help.HelpRouter.open = before
        }
    }

    @Test
    fun TitleBarLayout_EndsLeftOfTheCloseButton() {
        val bar = TitleBarLayout.bar(100f, 50f, 400f, 13f, 3f)
        assertEquals(19f, bar.height)
        val slots = TitleBarLayout.slots(bar, 13f, 2, closeButton = true, framePaddingX = 4f, spacing = 4f)
        // close at 500 - 4 - 13 = 483; ? ends 4 px left of it; Maximize 4 px left of the ?
        assertEquals(listOf(TitleBarLayout.Slot(449f, 53f), TitleBarLayout.Slot(466f, 53f)), slots)
        val noClose = TitleBarLayout.slots(bar, 13f, 1, closeButton = false, framePaddingX = 4f, spacing = 4f)
        assertEquals(TitleBarLayout.Slot(483f, 53f), noClose.single())
    }
}
