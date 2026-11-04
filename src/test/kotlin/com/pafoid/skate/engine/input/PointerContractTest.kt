package com.pafoid.skate.engine.input

import com.pafoid.skate.engine.core.LoggerService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE

/** The INP-09 pointer contract as seen through [RawInput] (what scripts, actions and runtime UI read). */
class PointerContractTest {

    private val provider = InputProvider(mockk<LoggerService>(relaxed = true))
    private val mouse = provider.mouseListener
    private val raw: RawInput = ProviderRawInput(provider)

    /** Mutable stand-in for the editor's ImGui desktop pointer. */
    private val desktop = FloatArray(2)

    private fun editorSetup() {
        mouse.pointerSource = PointerSource { it[0] = desktop[0]; it[1] = desktop[1] }
        // Editor on a second monitor: viewport image at desktop (2100, 350), 800x450 logical, 2x DPI target.
        mouse.setViewRect(ViewRect(2100f, 350f, 800f, 450f))
        mouse.setTargetSize(1600, 900)
    }

    private fun gameSetup() {
        // Standalone: the GLFW cursor (default source), framebuffer blitted over a 1280x720 window.
        mouse.setViewRect(ViewRect(0f, 0f, 1280f, 720f))
        mouse.setTargetSize(1920, 1080)
    }

    private fun press(button: Int) = mouse.mouseButtonCallback(0L, button, GLFW_PRESS, 0)
    private fun release(button: Int) = mouse.mouseButtonCallback(0L, button, GLFW_RELEASE, 0)

    @Test
    fun editorSource_positionIsTargetPixelsWhateverTheGlfwCursor() {
        editorSetup()
        desktop[0] = 2500f; desktop[1] = 575f
        mouse.mousePosCallback(0L, 12.0, 34.0) // window-relative cursor: must not leak into positions

        assertEquals(800f, raw.mouseX())
        assertEquals(450f, raw.mouseY())
    }

    @Test
    fun gameSource_positionIsTargetPixelsOfTheStretchedBlit() {
        gameSetup()
        mouse.mousePosCallback(0L, 640.0, 360.0)

        assertEquals(960f, raw.mouseX())
        assertEquals(540f, raw.mouseY())
        assertEquals(960f, provider.getMouseTargetPos().x)
    }

    @Test
    fun deltas_areCursorUnitsUnscaledByViewOrTarget() {
        editorSetup()
        mouse.mousePosCallback(0L, 100.0, 100.0)
        mouse.endFrame()
        mouse.mousePosCallback(0L, 110.0, 95.0)

        assertEquals(10f, raw.mouseDeltaX())
        assertEquals(-5f, raw.mouseDeltaY())
    }

    @Test
    fun editorSource_pressInsideTheGameViewReachesTheGameAndSurvivesLeavingIt() {
        editorSetup()
        desktop[0] = 2500f; desktop[1] = 575f
        press(0)

        assertTrue(raw.wasMouseButtonPressed(0))
        assertTrue(raw.isMouseButtonDown(0))

        mouse.endFrame()
        desktop[0] = 100f // dragged out over another panel
        assertFalse(raw.wasMouseButtonPressed(0))
        assertTrue(raw.isMouseButtonDown(0))

        release(0)
        assertFalse(raw.isMouseButtonDown(0))
    }

    @Test
    fun editorSource_pressOnAnotherPanelNeverReachesTheGame() {
        editorSetup()
        desktop[0] = 100f; desktop[1] = 100f
        press(1)
        assertFalse(raw.wasMouseButtonPressed(1))

        mouse.endFrame()
        desktop[0] = 2500f; desktop[1] = 575f // dragged into the view while held
        assertFalse(raw.isMouseButtonDown(1))
    }

    @Test
    fun pressResolvedAtEndOfFrameWhenNobodyQueriedIt() {
        editorSetup()
        desktop[0] = 2500f; desktop[1] = 575f
        press(1)
        mouse.endFrame() // no reader this frame; resolved here with the press-frame pointer

        desktop[0] = 100f
        assertTrue(raw.isMouseButtonDown(1))
    }

    @Test
    fun gameSource_buttonsFollowTheGlfwCursorInTheWindow() {
        gameSetup()
        mouse.mousePosCallback(0L, 640.0, 360.0)
        press(0)

        assertTrue(raw.wasMouseButtonPressed(0))
        assertTrue(raw.isMouseButtonDown(0))
        assertFalse(raw.isMouseButtonDown(42))
        assertFalse(raw.isMouseButtonDown(-1))
    }
}
