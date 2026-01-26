package com.pafoid.skate.engine.controls

import org.lwjgl.glfw.GLFW.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer

object JoystickListener {
    private val joystickPresent = BooleanArray(GLFW_JOYSTICK_LAST + 1)

    // Standard Gamepad Mapping (Xbox/PS)
    const val AXIS_LEFT_X = 0
    const val AXIS_LEFT_Y = 1
    const val AXIS_RIGHT_X = 2
    const val AXIS_RIGHT_Y = 3
    const val AXIS_LEFT_TRIGGER = 4
    const val AXIS_RIGHT_TRIGGER = 5

    const val BUTTON_A = 0
    const val BUTTON_B = 1
    const val BUTTON_X = 2
    const val BUTTON_Y = 3
    const val BUTTON_LB = 4
    const val BUTTON_RB = 5
    const val BUTTON_BACK = 6
    const val BUTTON_START = 7
    const val BUTTON_LS = 8
    const val BUTTON_RS = 9
    const val BUTTON_DPAD_UP = 10
    const val BUTTON_DPAD_RIGHT = 11
    const val BUTTON_DPAD_DOWN = 12
    const val BUTTON_DPAD_LEFT = 13
    
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
        val buttons: ByteBuffer? = glfwGetJoystickButtons(jid)
        return if (buttons != null) {
            val array = BooleanArray(buttons.remaining())
            for (i in array.indices) {
                array[i] = buttons.get() == 1.toByte()
            }
            array
        } else null
    }
}