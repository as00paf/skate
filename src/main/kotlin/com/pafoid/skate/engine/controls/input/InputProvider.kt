package com.pafoid.skate.engine.controls.input

import com.pafoid.skate.engine.controls.listeners.JoystickListener
import com.pafoid.skate.engine.controls.listeners.KeyListener
import org.lwjgl.glfw.GLFW.*

class InputProvider(
    private val joystickListener: JoystickListener,
    private val keyListener: KeyListener, ) : IInputProvider {
    override fun isKeyPressed(key: Int): Boolean = keyListener.isKeyPressed(key)
    override fun keyBeginPress(key: Int): Boolean = keyListener.keyBeginPress(key)
    override fun isJoystickPresent(jid: Int): Boolean = joystickListener.isJoystickPresent(jid)
    override fun getAxes(jid: Int): FloatArray? = joystickListener.getAxes(jid)
    override fun getButtons(jid: Int): BooleanArray? = joystickListener.getButtons(jid)
    override fun buttonPressed(jid: Int, button: Int): Boolean = joystickListener.buttonPressed(jid, button)
    override fun buttonBeginPress(jid: Int, button: Int): Boolean = joystickListener.buttonBeginPress(jid, button)
    override fun isCursorDisabled(): Boolean {
        val window = glfwGetCurrentContext()
        if (window == 0L) return false
        return glfwGetInputMode(window, GLFW_CURSOR) == GLFW_CURSOR_DISABLED
    }
}
