package com.pafoid.skate.engine.core

import org.lwjgl.glfw.GLFW

object Time {
    fun getTime(): Float = GLFW.glfwGetTime().toFloat()
}