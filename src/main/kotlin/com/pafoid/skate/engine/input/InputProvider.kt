package com.pafoid.skate.engine.input

import com.pafoid.skate.engine.input.listeners.GamepadListener
import com.pafoid.skate.engine.input.listeners.KeyListener
import org.lwjgl.glfw.GLFW.GLFW_CURSOR
import org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED
import org.lwjgl.glfw.GLFW.glfwGetCurrentContext
import org.lwjgl.glfw.GLFW.glfwGetInputMode

class InputProvider(
    private val gamepadListener: GamepadListener,
    private val keyListener: KeyListener,
) {
    fun initializeGamepad() {
        gamepadListener.init()
    }

    fun refreshGamepadState() {
        gamepadListener.update()
    }

    fun isKeyPressed(key: Int): Boolean = keyListener.isKeyPressed(key)
    fun keyBeginPress(key: Int): Boolean = keyListener.keyBeginPress(key)
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
