package com.pafoid.skate.engine.utils

import org.lwjgl.glfw.GLFW

object Time {
    fun getTime(): Float = GLFW.glfwGetTime().toFloat()
}