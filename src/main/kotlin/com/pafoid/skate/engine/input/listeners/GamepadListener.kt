package com.pafoid.skate.engine.input.listeners

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.input.ButtonStates
import com.pafoid.skate.engine.input.GamepadRegistry
import org.lwjgl.glfw.GLFW.GLFW_CONNECTED
import org.lwjgl.glfw.GLFW.GLFW_DISCONNECTED
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_LAST
import org.lwjgl.glfw.GLFW.glfwGetJoystickAxes
import org.lwjgl.glfw.GLFW.glfwGetJoystickButtons
import org.lwjgl.glfw.GLFW.glfwGetJoystickName
import org.lwjgl.glfw.GLFW.glfwJoystickPresent
import org.lwjgl.glfw.GLFW.glfwSetJoystickCallback
import java.nio.ByteBuffer
import java.nio.FloatBuffer

class GamepadListener(private val logger: LoggerService) {
    val registry = GamepadRegistry(GLFW_JOYSTICK_LAST + 1)
    private val buttonStates = ButtonStates(GLFW_JOYSTICK_LAST + 1)
    private var initialized = false

    fun init() {
        if (initialized) return

        registry.addListener { e ->
            if (e.connected) {
                logger.log("Joystick ${e.jid} connected: ${e.name}")
            } else {
                clearJoystickButtons(e.jid)
                logger.log("Joystick ${e.jid} disconnected")
            }
        }

        glfwSetJoystickCallback { jid, event ->
            when (event) {
                GLFW_CONNECTED -> registry.setPresent(jid, true) { glfwGetJoystickName(jid) }
                GLFW_DISCONNECTED -> registry.setPresent(jid, false)
            }
        }

        initialized = true
        refreshConnectedGamepads()
    }

    fun update() {
        if (!initialized) init()
        refreshConnectedGamepads()

        for (i in 0..GLFW_JOYSTICK_LAST) {
            if (!registry.isPresent(i)) {
                clearJoystickButtons(i)
                continue
            }

            val buttons: ByteBuffer? = glfwGetJoystickButtons(i)
            if (buttons != null) {
                val base = buttons.position()
                buttonStates.update(i, { buttons.get(base + it) == 1.toByte() }, buttons.remaining())
            }
        }
    }

    fun isGamepadPresent(jid: Int): Boolean = registry.isPresent(jid)

    /** Joystick id behind logical pad [pad] (0 = primary), or -1. Survives replugging into another slot. */
    fun joystickForPad(pad: Int): Int = registry.joystickForPad(pad)

    fun getAxes(jid: Int): FloatArray? {
        if (!isGamepadPresent(jid)) return null
        val axes: FloatBuffer? = glfwGetJoystickAxes(jid)
        return if (axes != null) {
            val array = FloatArray(axes.remaining())
            axes.get(array)
            array
        } else null
    }

    fun getButtons(jid: Int): BooleanArray? {
        if (!isGamepadPresent(jid)) return null
        return buttonStates.buttons(jid)
    }

    fun buttonPressed(jid: Int, button: Int): Boolean = isGamepadPresent(jid) && buttonStates.isDown(jid, button)

    fun buttonBeginPress(jid: Int, button: Int): Boolean = isGamepadPresent(jid) && buttonStates.beginPress(jid, button)

    fun buttonWasPressed(jid: Int, button: Int): Boolean = isGamepadPresent(jid) && buttonStates.changed(jid, button)

    fun refreshConnectedGamepads() {
        for (i in 0..GLFW_JOYSTICK_LAST) {
            registry.setPresent(i, glfwJoystickPresent(i)) { glfwGetJoystickName(i) }
        }
    }

    private fun clearJoystickButtons(jid: Int) = buttonStates.clear(jid)
}
