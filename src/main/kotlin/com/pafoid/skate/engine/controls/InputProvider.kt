package com.pafoid.skate.engine.controls

import org.lwjgl.glfw.GLFW.*

interface IInputProvider {
    fun isKeyPressed(key: Int): Boolean
    fun keyBeginPress(key: Int): Boolean
    fun isJoystickPresent(jid: Int): Boolean
    fun getAxes(jid: Int): FloatArray?
    fun getButtons(jid: Int): BooleanArray?
    fun buttonPressed(jid: Int, button: Int): Boolean
    fun buttonBeginPress(jid: Int, button: Int): Boolean
    fun isCursorDisabled(): Boolean
}

object InputProvider : IInputProvider {
    override fun isKeyPressed(key: Int): Boolean = KeyListener.isKeyPressed(key)
    override fun keyBeginPress(key: Int): Boolean = KeyListener.keyBeginPress(key)
    override fun isJoystickPresent(jid: Int): Boolean = JoystickListener.isJoystickPresent(jid)
    override fun getAxes(jid: Int): FloatArray? = JoystickListener.getAxes(jid)
    override fun getButtons(jid: Int): BooleanArray? = JoystickListener.getButtons(jid)
    override fun buttonPressed(jid: Int, button: Int): Boolean = JoystickListener.buttonPressed(jid, button)
    override fun buttonBeginPress(jid: Int, button: Int): Boolean = JoystickListener.buttonBeginPress(jid, button)
    override fun isCursorDisabled(): Boolean {
        val window = glfwGetCurrentContext()
        if (window == 0L) return false
        return glfwGetInputMode(window, GLFW_CURSOR) == GLFW_CURSOR_DISABLED
    }
}
