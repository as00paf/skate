package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.input.InputProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_A
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import org.lwjgl.glfw.GLFW.GLFW_PRESS

/** GUI-16: once ImGuiLayer.destroy() resets the hooks, reading input never reaches the destroyed ImGui context. */
class ImGuiCaptureHooksTest {
    private val input = InputProvider(LoggerService())

    /** Stands in for reading ImGui IO after destroyContext(), which would end the JVM. */
    private val destroyedContext: () -> Boolean = { error("ImGui context was destroyed") }

    private fun pressKeyAndButton() {
        input.keyListener.keyCallback(0L, GLFW_KEY_A, 0, GLFW_PRESS, 0)
        input.mouseListener.mouseButtonCallback(0L, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0)
    }

    @Test
    fun `installed hooks gate input while ImGui wants capture`() {
        ImGuiCaptureHooks.install(input, keyboard = { true }, mouse = { true })
        pressKeyAndButton()

        assertFalse(input.keyListener.isKeyPressed(GLFW_KEY_A))
        assertFalse(input.mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT))
    }

    @Test
    fun `reading input after reset does not touch the destroyed context`() {
        ImGuiCaptureHooks.install(input, destroyedContext, destroyedContext)
        ImGuiCaptureHooks.reset(input)
        pressKeyAndButton()

        assertTrue(input.keyListener.isKeyPressed(GLFW_KEY_A))
        assertTrue(input.mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT))
        assertFalse(input.keyListener.captureKeyboard())
        assertFalse(input.mouseListener.captureMouse())
    }
}
