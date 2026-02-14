package com.pafoid.skate.engine.input

import com.pafoid.skate.engine.input.listeners.GamepadConstants
import com.pafoid.skate.engine.input.listeners.JoystickListener
import com.pafoid.skate.engine.input.listeners.KeyListener
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.GLFW_CURSOR
import org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED
import org.lwjgl.glfw.GLFW.glfwGetCurrentContext
import org.lwjgl.glfw.GLFW.glfwGetInputMode

class InputProvider(
    private val joystickListener: JoystickListener,
    private val keyListener: KeyListener, ) : IInputProvider {
    override fun isKeyPressed(key: Int): Boolean = keyListener.isKeyPressed(key)
    override fun keyBeginPress(key: Int): Boolean = keyListener.keyBeginPress(key)
    override fun isJoystickPresent(jid: Int): Boolean = joystickListener.isJoystickPresent(jid)
    override fun getAxes(jid: Int): FloatArray? = joystickListener.getAxes(jid)
    override fun getMovementVector(jid: Int): Vector3f {
        val threshold = 0.15f
        var moveX = 0f
        var moveZ = 0f

        getAxes(jid)?.let { axes ->
            if (axes.size > GamepadConstants.AXIS_LEFT_Y) {
                moveZ = -axes[GamepadConstants.AXIS_LEFT_Y]
                moveX = axes[GamepadConstants.AXIS_LEFT_X]
            }
        }

        val moveInput = Vector3f(moveX, 0f, moveZ)
        val magnitude = moveInput.length()

        if (magnitude < threshold) {
            return Vector3f()
        }

        // Normalize direction
        val direction = Vector3f(moveInput).normalize()

        // Re-scale magnitude so it smoothly maps from [threshold → 1] to [0 → 1]
        val scaledMagnitude = (magnitude - threshold) / (1f - threshold)

        return direction.mul(scaledMagnitude)
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
