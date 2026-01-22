package com.pafoid.skate.util

import org.lwjgl.glfw.GLFW

object Time {

    var timeStarted = System.nanoTime()

    fun getTime(): Float = GLFW.glfwGetTime().toFloat()//((System.nanoTime() - timeStarted) * 1E-9).toFloat()
}