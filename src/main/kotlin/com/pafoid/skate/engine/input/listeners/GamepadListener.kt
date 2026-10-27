package com.pafoid.skate.engine.input.listeners

import com.pafoid.skate.engine.core.LoggerService
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
import kotlin.math.min

class GamepadListener(private val logger: LoggerService) {
    val registry = GamepadRegistry(GLFW_JOYSTICK_LAST + 1)
    private val lastButtons = Array(GLFW_JOYSTICK_LAST + 1) { BooleanArray(15) { false } }
    private val currentButtons = Array(GLFW_JOYSTICK_LAST + 1) { BooleanArray(15) { false } }
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

            for (j in 0 until 15) {
                lastButtons[i][j] = currentButtons[i][j]
            }

            val buttons: ByteBuffer? = glfwGetJoystickButtons(i)
            if (buttons != null) {
                for (j in 0 until 15) {
                    currentButtons[i][j] = false
                }

                for (j in 0 until min(buttons.remaining(), 15)) {
                    currentButtons[i][j] = buttons.get() == 1.toByte()
                }
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
        return currentButtons[jid]
    }

    fun buttonPressed(jid: Int, button: Int): Boolean {
        if (!isGamepadPresent(jid) || button >= 15) return false
        return currentButtons[jid][button]
    }

    fun buttonBeginPress(jid: Int, button: Int): Boolean {
        if (!isGamepadPresent(jid) || button >= 15) return false
        return currentButtons[jid][button] && !lastButtons[jid][button]
    }

    fun buttonWasPressed(jid: Int, button: Int): Boolean {
        if (!isGamepadPresent(jid) || button >= 15) return false
        return (!currentButtons[jid][button] && lastButtons[jid][button]) || (currentButtons[jid][button] && !lastButtons[jid][button])
    }

    fun refreshConnectedGamepads() {
        for (i in 0..GLFW_JOYSTICK_LAST) {
            registry.setPresent(i, glfwJoystickPresent(i)) { glfwGetJoystickName(i) }
        }
    }

    private fun clearJoystickButtons(jid: Int) {
        for (j in 0 until 15) {
            currentButtons[jid][j] = false
            lastButtons[jid][j] = false
        }
    }
}
