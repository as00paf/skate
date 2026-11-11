package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.animation.mocap.MocapPhase
import com.pafoid.skate.editor.animation.mocap.VideoMocapOptions
import com.pafoid.skate.editor.animation.mocap.VideoToAnimationController
import com.pafoid.skate.editor.animation.mocap.VideoInfo
import com.pafoid.skate.editor.animation.mocap.VideoPlayer
import com.pafoid.skate.editor.ui.windows.assetBrowser.TextureThumbnail
import com.pafoid.skate.engine.render.rhi.RhiTexture
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

    private val frameFiles = (0..1).map { kotlin.io.path.createTempFile("mocap_frame$it", ".png").toFile().apply { deleteOnExit() } }

    /**
     * The window as the human sees it after choosing a video (when [loaded]): video info, the preview frame (a fake
     * texture, no GPU), the playback buttons, the scrub bar with the in/out range and the range buttons. [playing]
     * plays the video, so the preview frame changes every frame.
     */
    private fun window(loaded: Boolean = false, playing: Boolean = false): VideoToAnimationWindow {
        val fakePlayer = if (loaded) VideoPlayer(30f, 300).apply { markIn(30); markOut(200); seek(90); if (playing) togglePlay() } else null
        var shown = 0
        val strings = mockk<StringManager>(relaxed = true)
        every { strings.getString(any()) } answers { firstArg() }
        every { strings.getString(any(), *anyVararg()) } answers { firstArg() }
        every { strings.getString("lbl.mocap.setup") } answers { setupY = ImGui.getCursorScreenPosY(); "lbl.mocap.setup" }
        val engine = mockk<Engine>(relaxed = true) { every { stringManager } returns strings }
        val editor = mockk<Editor>(relaxed = true)
        val projects = mockk<ProjectManager>(relaxed = true) { every { getProjectDirectory() } returns File("project") }
        val controller = mockk<VideoToAnimationController>(relaxed = true) {
            every { phase } returns MocapPhase.IDLE
            every { video } returns if (loaded) File("clip.mp4") else null
            every { info } returns if (loaded) VideoInfo(30f, 300, 1920, 1080, 10f, frameFiles[0]) else null
            every { player } returns fakePlayer
            every { tick(any()) } answers { fakePlayer?.tick(firstArg()) }
            every { previewImage() } answers { if (loaded) frameFiles[(shown++ / 2) % 2] else null }
            every { previewProgress } returns null
            every { landmarks } returns null
            every { busy } returns false
            every { canCreate } returns false
            every { options } returns VideoMocapOptions()
            every { result } returns null
            every { error } returns null
            every { setup } returns null
            every { checkingSetup } returns false
        }
        return VideoToAnimationWindow(engine, editor, projects, controller).also {
            it.isOpen.set(true)
            it.uploadPreview = { _ -> TextureThumbnail(mockk<RhiTexture>(relaxed = true) { every { nativeHandle } returns 1L }, 512, 288, 1920, 1080) }
        }
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

    /** Scrolls [window] to the end with the mouse wheel; the Setup header must end up fully visible and stay put. */
    private fun assertScrollsToTheEnd(window: VideoToAnimationWindow, case: String) {
        repeat(3) { frame(window) }
        val bottom = winY + winH
        val before = setupY
        assertTrue(before > bottom, "$case: the Setup header starts below a $winH px window (y=$before, bottom=$bottom)")

        repeat(40) { frame(window, wheel = -5f) }
        repeat(3) { frame(window) }
        val after = setupY
        val headerHeight = ImGui.getFrameHeight()
        assertTrue(after + headerHeight <= bottom, "$case: scrolled to the end, the Setup header is fully visible (y=$after, bottom=$bottom)")
        assertTrue(after < before, "$case: the content moved up")

        repeat(3) { frame(window) }
        assertEquals(after, setupY, "$case: the scroll position holds (no snapping back)")
    }

    @Test
    fun SmallWindow_ScrollsToItsLastControl() {
        assertScrollsToTheEnd(window(), "no video")
    }

    @Test
    fun SmallWindow_WithAVideoChosen_ScrollsToItsLastControl() {
        assertScrollsToTheEnd(window(loaded = true), "video chosen")
    }

    @Test
    fun SmallWindow_WhileTheVideoPlays_ScrollsToItsLastControl() {
        assertScrollsToTheEnd(window(loaded = true, playing = true), "video playing")
    }
}
