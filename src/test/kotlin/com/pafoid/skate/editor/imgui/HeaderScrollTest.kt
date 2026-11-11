package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * EDT-85 guard: a small real ImGui window whose whole body (header included) scrolls. The header's clip clearance
 * (EDT-72) pushed the row back below the window top on every frame, so the content never moved and the scroll
 * snapped back. Scrolling must move the header and the content together and keep the scroll position.
 */
class HeaderScrollTest {

    private val strings: StringManager = mockk(relaxed = true)

    private class Probe(private val strings: StringManager, private val windowPaddingY: Float?) : EditorWindow("window.probe") {
        override val icon: String = Icons.LIST
        var requestScroll: Float? = null
        var scrollY = 0f
        var maxScrollY = 0f
        var bodyTop = 0f
        var windowTop = 0f

        override fun imgui() {
            windowPaddingY?.let { ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, ImGui.getStyle().windowPaddingX, it) }
            if (beginWindow(strings)) {
                requestScroll?.let { ImGui.setScrollY(it); requestScroll = null }
                header(strings) { ImGui.text("tools") }
                bodyTop = ImGui.getCursorScreenPosY()
                repeat(60) { ImGui.text("line $it") }
                scrollY = ImGui.getScrollY()
                maxScrollY = ImGui.getScrollMaxY()
                windowTop = ImGui.getWindowPosY()
            }
            ImGui.end()
            windowPaddingY?.let { ImGui.popStyleVar() }
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
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        ImGui.destroyContext()
    }

    private fun frames(probe: Probe, n: Int) = repeat(n) {
        ImGui.newFrame()
        ImGui.setNextWindowPos(120.4f, 75.6f)
        ImGui.setNextWindowSize(260f, 160f)
        probe.imgui()
        ImGui.render()
    }

    @Test
    fun ScrollingMovesTheHeaderAndTheBody_AndTheScrollSticks() {
        val failures = mutableListOf<String>()
        for (scale in listOf(1f, 1.5f)) for (padY in listOf(null, 0f)) {
            ImGuiStyleManager.uiScale = { scale }
            ImGuiStyleManager.invalidate()
            ImGuiStyleManager.sync("dark")
            val probe = Probe(strings, padY).also { it.isOpen.set(true) }
            val case = "@${(scale * 100).toInt()}% padY=${padY ?: "theme"}"

            frames(probe, 3)
            val bodyTop0 = probe.bodyTop
            val button0 = WindowHeader.lastDrawn!!.button
            if (probe.maxScrollY < 100f) failures += "$case: window does not scroll (max ${probe.maxScrollY})"

            probe.requestScroll = 80f
            frames(probe, 4)
            if (kotlin.math.abs(probe.scrollY - 80f) > 0.5f) failures += "$case: scroll snapped to ${probe.scrollY}"
            val moved = bodyTop0 - probe.bodyTop
            if (kotlin.math.abs(moved - 80f) > 1f) failures += "$case: body moved $moved px, not 80"
            val buttonMoved = button0.minY - WindowHeader.lastDrawn!!.button.minY
            if (kotlin.math.abs(buttonMoved - 80f) > 1f) failures += "$case: header moved $buttonMoved px, not 80"

            // Back at the top, the `?` is clear of the clip top again (EDT-72).
            probe.requestScroll = 0f
            frames(probe, 3)
            val (button, clip) = WindowHeader.lastDrawn!!
            if (button.minY < clip.minY + HelpButtonLayout.MIN_CLIP_CLEARANCE) failures += "$case: ? clipped at top: $button $clip"
            if (button.minY != kotlin.math.floor(button.minY)) failures += "$case: ? not on a whole pixel: $button"
            assertEquals(0f, probe.scrollY, "$case scroll back to top")
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
