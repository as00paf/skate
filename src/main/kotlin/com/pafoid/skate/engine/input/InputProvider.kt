package com.pafoid.skate.engine.input

import com.pafoid.skate.engine.input.listeners.GamepadConstants
import com.pafoid.skate.engine.input.listeners.JoystickListener
import com.pafoid.skate.engine.input.listeners.KeyListener
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*

class InputProvider(
    private val joystickListener: JoystickListener,
    private val keyListener: KeyListener, ) : IInputProvider {
    override fun isKeyPressed(key: Int): Boolean = keyListener.isKeyPressed(key)
    override fun keyBeginPress(key: Int): Boolean = keyListener.keyBeginPress(key)
    override fun isJoystickPresent(jid: Int): Boolean = joystickListener.isJoystickPresent(jid)
    override fun getAxes(jid: Int): FloatArray? = joystickListener.getAxes(jid)
    override fun getMovementVector(jid: Int): Vector3f {
        val threshold = 0.4f
        var moveX = 0f
        var moveZ = 0f

        getAxes(jid)?.let { axes ->
            if (axes.size > GamepadConstants.AXIS_LEFT_Y) {
                moveZ = -axes[GamepadConstants.AXIS_LEFT_Y]
                moveX = axes[GamepadConstants.AXIS_LEFT_X]
            }
        }

        val moveInput = Vector3f(moveX, 0f, moveZ)
        if (moveInput.length() > 1f) moveInput.normalize()

        val result = if (moveInput.length() > threshold) moveInput else Vector3f()

        return result
    }
    override fun getButtons(jid: Int): BooleanArray? = joystickListener.getButtons(jid)
    override fun buttonPressed(jid: Int, button: Int): Boolean = joystickListener.buttonPressed(jid, button)
    override fun buttonBeginPress(jid: Int, button: Int): Boolean = joystickListener.buttonBeginPress(jid, button)
    override fun isCursorDisabled(): Boolean {
        val window = glfwGetCurrentContext()
        if (window == 0L) return false
        return glfwGetInputMode(window, GLFW_CURSOR) == GLFW_CURSOR_DISABLED
    }
}
