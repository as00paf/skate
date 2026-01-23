package com.pafoid.skate.engine

import org.lwjgl.glfw.GLFW

object Time {
    fun getTime(): Float = GLFW.glfwGetTime().toFloat()
}