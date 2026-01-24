package com.pafoid.skate.engine.controls

import org.lwjgl.glfw.GLFW.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer

object JoystickListener {
    private val joystickPresent = BooleanArray(GLFW_JOYSTICK_LAST + 1)
    
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