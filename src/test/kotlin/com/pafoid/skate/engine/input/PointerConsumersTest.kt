package com.pafoid.skate.engine.input

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.systems.ReaderFlyInputSource
import com.pafoid.skate.engine.ui.ReaderUiInputSource
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE

/** EDT-35: fly-cam look and the runtime UI pointer read the engine's reader, as wired in `Engine`. */
class PointerConsumersTest {

    private val provider = InputProvider(mockk<LoggerService>(relaxed = true))
    private val mouse = provider.mouseListener
    private val reader = ActionInputReader(ProviderRawInput(provider))
    private val fly = ReaderFlyInputSource(reader)
    private val ui = ReaderUiInputSource(reader)

    /** Editor ImGui desktop pointer; view at (2100, 350) 800x450, 1600x900 target. */
    private val desktop = floatArrayOf(2500f, 575f)

    init {
        mouse.pointerSource = PointerSource { it[0] = desktop[0]; it[1] = desktop[1] }
        mouse.setViewRect(ViewRect(2100f, 350f, 800f, 450f))
        mouse.setTargetSize(1600, 900)
    }

    private fun button(b: Int, action: Int) = mouse.mouseButtonCallback(0L, b, action, 0)
    private fun move(x: Double, y: Double) = mouse.mousePosCallback(0L, x, y)

    @Test
    fun flyLook_followsDeltasWhileRightDragStartedInView() {
        move(100.0, 100.0); mouse.endFrame()
        button(1, GLFW_PRESS)
        move(110.0, 96.0)
        val f = fly.read()
        assertEquals(10f, f.lookDx)
        assertEquals(-4f, f.lookDy)
    }

    @Test
    fun flyLook_keepsWorkingWhenTheCursorLeavesTheViewMidDrag() {
        button(1, GLFW_PRESS)
        assertEquals(0f, fly.read().lookDx) // resolved inside the view
        mouse.endFrame()

        desktop[0] = 50f // over another panel now
        move(20.0, 0.0)
        assertEquals(20f, fly.read().lookDx)

        button(1, GLFW_RELEASE); mouse.endFrame()
        move(40.0, 0.0)
        assertEquals(0f, fly.read().lookDx)
    }

    @Test
    fun flyLook_ignoresARightDragStartedOutsideTheView() {
        desktop[0] = 50f
        button(1, GLFW_PRESS); mouse.endFrame()
        desktop[0] = 2500f // dragged into the view
        move(30.0, 0.0)
        assertEquals(0f, fly.read().lookDx)
    }

    @Test
    fun noLookWithoutTheRightButton() {
        move(10.0, 10.0)
        assertEquals(0f, fly.read().lookDx)
    }

    @Test
    fun uiPointer_isTargetPixelsWithGameViewButton() {
        val hover = ui.frame(0f)
        assertEquals(800f, hover.pointerX)
        assertEquals(450f, hover.pointerY)
        assertFalse(hover.pointerDown)

        button(0, GLFW_PRESS)
        val pressed = ui.frame(0f)
        assertTrue(pressed.pointerPressed && pressed.pointerDown)
        mouse.endFrame()

        desktop[0] = 50f // drag a UI slider out of the view: still held
        assertTrue(ui.frame(0f).pointerDown)
        button(0, GLFW_RELEASE)
        assertTrue(ui.frame(0f).pointerReleased)
    }

    @Test
    fun uiPointer_pressOnAnotherPanelDoesNotPressTheUi() {
        desktop[0] = 50f
        button(0, GLFW_PRESS); mouse.endFrame()
        desktop[0] = 2500f
        val f = ui.frame(0f)
        assertFalse(f.pointerDown)
        assertFalse(f.pointerPressed)
    }
}
