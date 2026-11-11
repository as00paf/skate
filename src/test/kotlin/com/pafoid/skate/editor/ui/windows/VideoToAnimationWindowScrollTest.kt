package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.animation.mocap.MocapPhase
import com.pafoid.skate.editor.animation.mocap.VideoMocapOptions
import com.pafoid.skate.editor.animation.mocap.VideoToAnimationController
import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.loadFonts
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * ANI-22: renders the real Video to Animation window (real ImGui frames, no GPU) at a small size and scrolls it with
 * the mouse wheel. The last control, the Setup section, must start out of view and become fully reachable.
 */
class VideoToAnimationWindowScrollTest {

    private val winX = 100f
    private val winY = 80f
    private val winW = 320f
    private val winH = 140f

    /** Screen Y where the Setup header was drawn in the last frame (recorded when the window asks for its label). */
    private var setupY = Float.NaN

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
        ImGuiStyleManager.sync("dark")
    }

    @AfterEach
    fun teardown() {
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        ImGui.destroyContext()
    }

    private fun window(): VideoToAnimationWindow {
        val strings = mockk<StringManager>(relaxed = true)
        every { strings.getString(any()) } answers { firstArg() }
        every { strings.getString(any(), *anyVararg()) } answers { firstArg() }
        every { strings.getString("lbl.mocap.setup") } answers { setupY = ImGui.getCursorScreenPosY(); "lbl.mocap.setup" }
        val engine = mockk<Engine>(relaxed = true) { every { stringManager } returns strings }
        val editor = mockk<Editor>(relaxed = true)
        val projects = mockk<ProjectManager>(relaxed = true) { every { getProjectDirectory() } returns File("project") }
        val controller = mockk<VideoToAnimationController>(relaxed = true) {
            every { phase } returns MocapPhase.IDLE
            every { video } returns null
            every { info } returns null
            every { player } returns null
            every { busy } returns false
            every { canCreate } returns false
            every { options } returns VideoMocapOptions()
            every { result } returns null
            every { error } returns null
            every { setup } returns null
            every { checkingSetup } returns false
        }
        return VideoToAnimationWindow(engine, editor, projects, controller).also { it.isOpen.set(true) }
    }

    private fun frame(window: VideoToAnimationWindow, wheel: Float = 0f) {
        val io = ImGui.getIO()
        io.addMousePosEvent(winX + winW / 2, winY + winH - 30f)
        if (wheel != 0f) io.addMouseWheelEvent(0f, wheel)
        ImGui.newFrame()
        ImGui.setNextWindowPos(winX, winY)
        ImGui.setNextWindowSize(winW, winH)
        window.imgui()
        ImGui.render()
    }

    @Test
    fun SmallWindow_ScrollsToItsLastControl() {
        val window = window()
        repeat(3) { frame(window) }
        val bottom = winY + winH
        val before = setupY
        assertTrue(before > bottom, "the Setup header starts below a $winH px window (y=$before, bottom=$bottom)")

        repeat(20) { frame(window, wheel = -5f) }
        repeat(3) { frame(window) }
        val after = setupY
        val headerHeight = ImGui.getFrameHeight()
        assertTrue(after + headerHeight <= bottom, "scrolled to the end, the Setup header is fully visible (y=$after, bottom=$bottom)")
        assertTrue(after < before, "the content moved up")

        frame(window)
        assertEquals(after, setupY, "the scroll position holds (no snapping back)")
    }
}
