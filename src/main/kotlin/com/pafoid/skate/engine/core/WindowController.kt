package com.pafoid.skate.engine.core

import org.lwjgl.glfw.GLFW.GLFW_MAXIMIZED
import org.lwjgl.glfw.GLFW.GLFW_TRUE
import org.lwjgl.glfw.GLFW.glfwGetWindowAttrib
import org.lwjgl.glfw.GLFW.glfwGetWindowPos
import org.lwjgl.glfw.GLFW.glfwGetWindowSize
import org.lwjgl.glfw.GLFW.glfwIconifyWindow
import org.lwjgl.glfw.GLFW.glfwMaximizeWindow
import org.lwjgl.glfw.GLFW.glfwRestoreWindow
import org.lwjgl.glfw.GLFW.glfwSetWindowPos
import org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose

/**
 * Handles custom window operations.
 * Provides functionality for minimizing, maximizing, restoring, and closing the window.
 */
class WindowController(private val glfwWindow: Long) {

    // Store window size before maximizing for proper restore
    private var preMaximizeWidth = 1920
    private var preMaximizeHeight = 1080
    private var preMaximizeX = 0
    private var preMaximizeY = 10

    /**
     * Minimizes the window to the taskbar.
     */
    fun minimize() {
        glfwIconifyWindow(glfwWindow)
    }

    /**
     * Toggles between maximized and restored states.
     */
    fun toggleMaximize() {
        if (isMaximized()) {
            restore()
        } else {
            maximize()
        }
    }

    /**
     * Maximizes the window.
     */
    fun maximize() {
        val widthBuffer = IntArray(1)
        val heightBuffer = IntArray(1)
        val xBuffer = IntArray(1)
        val yBuffer = IntArray(1)
        glfwGetWindowSize(glfwWindow, widthBuffer, heightBuffer)
        glfwGetWindowPos(glfwWindow, xBuffer, yBuffer)
        preMaximizeWidth = widthBuffer[0]
        preMaximizeHeight = heightBuffer[0]
        preMaximizeX = xBuffer[0]
        preMaximizeY = yBuffer[0]

        glfwMaximizeWindow(glfwWindow)
    }

    /**
     * Restores the window.
     */
    fun restore() {
        glfwRestoreWindow(glfwWindow)
        glfwSetWindowPos(glfwWindow, preMaximizeX, preMaximizeY)
    }

    /**
     * Closes the window.
     */
    fun close() {
        glfwSetWindowShouldClose(glfwWindow, true)
    }

    /**
     * Checks if the window is currently maximized.
     */
    fun isMaximized(): Boolean {
        return glfwGetWindowAttrib(glfwWindow, GLFW_MAXIMIZED) == GLFW_TRUE
    }
}

