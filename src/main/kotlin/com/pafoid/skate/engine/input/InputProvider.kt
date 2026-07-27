package com.pafoid.skate.engine.input

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.Time
import com.pafoid.skate.engine.input.listeners.GamepadListener
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_CURSOR
import org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT
import org.lwjgl.glfw.GLFW.glfwGetCurrentContext
import org.lwjgl.glfw.GLFW.glfwGetInputMode

class InputProvider(
    val logger: LoggerService
) {
    private val inputBuffer: InputBuffer = InputBuffer()

    val gamepadListener: GamepadListener = GamepadListener(logger)
    val keyListener: KeyListener = KeyListener()
    val mouseListener: MouseListener = MouseListener()

    fun initializeGamepad() {
        gamepadListener.init()
    }

    fun refreshGamepadState() {
        gamepadListener.update()
    }

    fun endFrame() {
        inputBuffer.push(
            Time.getTime(),
            getMousePos(),
            getAxes(GLFW.GLFW_JOYSTICK_1)
        )
        keyListener.endFrame()
        mouseListener.endFrame()
    }

    // Keys
    fun isKeyPressed(key: Int): Boolean = keyListener.isKeyPressed(key)
    fun keyBeginPress(key: Int): Boolean = keyListener.keyBeginPress(key)
    fun isControlKeyDown(): Boolean =
        isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL)

    // Mouse
    fun getMouseX() = mouseListener.getX()
    fun getMouseY() = mouseListener.getY()
    fun getMousePos() = Vector2f(getMouseX(), getMouseY())
    fun getMouseDx() = mouseListener.dx
    fun getMouseDy() = mouseListener.dy
    fun getMouseScreenX() = mouseListener.getScreenX()
    fun getMouseScreenY() = mouseListener.getScreenY()
    fun getMouseScreenPos() = Vector2f(getMouseScreenX(), getMouseScreenY())
    fun getNormalizedMousePos() = Vector2f(mouseListener.getNormalizedX(), mouseListener.getNormalizedY())
    fun getGameViewportPos() = mouseListener.gameViewportPos
    fun getGameViewportSize() = mouseListener.gameViewportSize

    fun isLeftMouseButtonDown(ignoreImGui: Boolean = false) =
        mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT, ignoreImGui)

    fun isRightMouseButtonDown(ignoreImGui: Boolean = false) =
        mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_RIGHT, ignoreImGui)

    fun isMiddleMouseButtonDown(ignoreImGui: Boolean = false) =
        mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_MIDDLE, ignoreImGui)

    fun isMouseButtonDown(button: Int, ignoreImGui: Boolean = false) =
        mouseListener.isMouseButtonDown(button, ignoreImGui)

    fun mouseButtonBeginPress(button: Int) = mouseListener.mouseButtonBeginPress(button)

    fun isInsideViewport(): Boolean {
        return mouseListener.getX() >= getGameViewportPos().x && mouseListener.getX() <= (getGameViewportPos().x + getGameViewportSize().x) &&
                mouseListener.getY() >= getGameViewportPos().y && mouseListener.getY() <= (getGameViewportPos().y + getGameViewportSize().y)
    }

    fun getMouseScrollY() = mouseListener.getScrollY()

    // Gamepad
    fun isJoystickPresent(jid: Int): Boolean = gamepadListener.isGamepadPresent(jid)
    fun getAxes(jid: Int): FloatArray? = gamepadListener.getAxes(jid)
    fun getButtons(jid: Int): BooleanArray? = gamepadListener.getButtons(jid)
    fun buttonPressed(jid: Int, button: Int): Boolean = gamepadListener.buttonPressed(jid, button)
    fun buttonWasPressed(jid: Int, button: Int): Boolean = gamepadListener.buttonWasPressed(jid, button)
    fun buttonBeginPress(jid: Int, button: Int): Boolean = gamepadListener.buttonBeginPress(jid, button)
    fun isCursorDisabled(): Boolean {
        val window = glfwGetCurrentContext()
        if (window == 0L) return false
        return glfwGetInputMode(window, GLFW_CURSOR) == GLFW_CURSOR_DISABLED
    }
}
