package com.pafoid.skate.engine.input.listeners

import com.pafoid.skate.editor.systems.LoggerService
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
    private val gamepadPresent = BooleanArray(GLFW_JOYSTICK_LAST + 1)
    private val lastButtons = Array(GLFW_JOYSTICK_LAST + 1) { BooleanArray(15) { false } }
    private val currentButtons = Array(GLFW_JOYSTICK_LAST + 1) { BooleanArray(15) { false } }
    
    fun init() {
        for (i in 0..GLFW_JOYSTICK_LAST) {
            gamepadPresent[i] = glfwJoystickPresent(i)
        }
        
        glfwSetJoystickCallback { jid, event ->
            if (event == GLFW_CONNECTED) {
                gamepadPresent[jid] = true
                logger.logEngine("Joystick $jid connected: ${glfwGetJoystickName(jid)}")
            } else if (event == GLFW_DISCONNECTED) {
                gamepadPresent[jid] = false
                logger.logEngine("Joystick $jid disconnected")
            }
        }
    }

    fun update() {
        for (i in 0..GLFW_JOYSTICK_LAST) {
            if (gamepadPresent[i]) {
                for (j in 0 until 15) {
                    lastButtons[i][j] = currentButtons[i][j]
                }
                
                val buttons: ByteBuffer? = glfwGetJoystickButtons(i)
                if (buttons != null) {
                    for (j in 0 until min(buttons.remaining(), 15)) {
                        currentButtons[i][j] = buttons.get() == 1.toByte()
                    }
                }
            }
        }
    }

    fun isGamepadPresent(jid: Int): Boolean = gamepadPresent[jid]

    fun getAxes(jid: Int): FloatArray? {
        if (!gamepadPresent[jid]) return null
        val axes: FloatBuffer? = glfwGetJoystickAxes(jid)
        return if (axes != null) {
            val array = FloatArray(axes.remaining())
            axes.get(array)
            array
        } else null
    }

    fun getButtons(jid: Int): BooleanArray? {
        if (!gamepadPresent[jid]) return null
        return currentButtons[jid]
    }

    fun buttonPressed(jid: Int, button: Int): Boolean {
        if (!gamepadPresent[jid] || button >= 15) return false
        return currentButtons[jid][button]
    }

    fun buttonBeginPress(jid: Int, button: Int): Boolean {
        if (!gamepadPresent[jid] || button >= 15) return false
        return currentButtons[jid][button] && !lastButtons[jid][button]
    }

    fun buttonWasPressed(jid: Int, button: Int): Boolean {
        if (!gamepadPresent[jid] || button >= 15) return false
        return (!currentButtons[jid][button] && lastButtons[jid][button]) || (currentButtons[jid][button] && !lastButtons[jid][button])
    }
}