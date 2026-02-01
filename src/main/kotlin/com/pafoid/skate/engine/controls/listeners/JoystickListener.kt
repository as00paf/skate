package com.pafoid.skate.engine.controls.listeners

import org.lwjgl.glfw.GLFW.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import kotlin.math.min

class JoystickListener {
    private val joystickPresent = BooleanArray(GLFW_JOYSTICK_LAST + 1)
    private val lastButtons = Array(GLFW_JOYSTICK_LAST + 1) { BooleanArray(15) { false } }
    private val currentButtons = Array(GLFW_JOYSTICK_LAST + 1) { BooleanArray(15) { false } }
    
    fun init() {
        for (i in 0..GLFW_JOYSTICK_LAST) {
            joystickPresent[i] = glfwJoystickPresent(i)
        }
        
        glfwSetJoystickCallback { jid, event ->
            if (event == GLFW_CONNECTED) {
                joystickPresent[jid] = true
                println("Joystick $jid connected: ${glfwGetJoystickName(jid)}")
            } else if (event == GLFW_DISCONNECTED) {
                joystickPresent[jid] = false
                println("Joystick $jid disconnected")
            }
        }
    }

    fun update() {
        for (i in 0..GLFW_JOYSTICK_LAST) {
            if (joystickPresent[i]) {
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

    fun isJoystickPresent(jid: Int): Boolean = joystickPresent[jid]

    fun getAxes(jid: Int): FloatArray? {
        if (!joystickPresent[jid]) return null
        val axes: FloatBuffer? = glfwGetJoystickAxes(jid)
        return if (axes != null) {
            val array = FloatArray(axes.remaining())
            axes.get(array)
            array
        } else null
    }

    fun getButtons(jid: Int): BooleanArray? {
        if (!joystickPresent[jid]) return null
        return currentButtons[jid]
    }

    fun buttonPressed(jid: Int, button: Int): Boolean {
        if (!joystickPresent[jid] || button >= 15) return false
        return currentButtons[jid][button]
    }

    fun buttonBeginPress(jid: Int, button: Int): Boolean {
        if (!joystickPresent[jid] || button >= 15) return false
        return currentButtons[jid][button] && !lastButtons[jid][button]
    }
}