package com.pafoid.skate.engine.core

import org.lwjgl.glfw.GLFW.GLFW_DECORATED
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_MAXIMIZED
import org.lwjgl.glfw.GLFW.GLFW_TRUE
import org.lwjgl.glfw.GLFW.glfwGetWindowAttrib
import org.lwjgl.glfw.GLFW.glfwGetWindowPos
import org.lwjgl.glfw.GLFW.glfwGetWindowSize
import org.lwjgl.glfw.GLFW.glfwIconifyWindow
import org.lwjgl.glfw.GLFW.glfwMaximizeWindow
import org.lwjgl.glfw.GLFW.glfwRestoreWindow
import org.lwjgl.glfw.GLFW.glfwSetWindowAttrib
import org.lwjgl.glfw.GLFW.glfwSetWindowPos
import org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose
import org.lwjgl.glfw.GLFW.glfwSetWindowSize

/**
 * Handles custom window operations for a GLFW window without decorations.
 * Provides functionality for minimizing, maximizing, restoring, closing, and dragging the window.
 */
class WindowController(private val glfwWindow: Long) {

    private var isDragging = false
    private var dragOffsetX = 0.0
    private var dragOffsetY = 0.0

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
        // Store current window size and position before maximizing
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
        
        glfwSetWindowAttrib(glfwWindow, GLFW_DECORATED, GLFW_FALSE)
        glfwMaximizeWindow(glfwWindow)
    }

    /**
     * Restores the window from a maximized or minimized state.
     */
    fun restore() {
        // First enable decorations while still maximized
        glfwSetWindowAttrib(glfwWindow, GLFW_DECORATED, GLFW_TRUE)
        // Then restore the window size/position
        glfwRestoreWindow(glfwWindow)
        // Finally set the window to the pre-maximize size and position
        // (glfwRestoreWindow might not restore to the exact size we want)
        glfwSetWindowSize(glfwWindow, preMaximizeWidth, preMaximizeHeight)
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

    /**
     * Starts dragging the window from the current mouse position.
     * Should be called when the custom title bar is clicked.
     */
    fun startDrag(mouseX: Double, mouseY: Double) {
        if (isMaximized()) return
        isDragging = true
        dragOffsetX = mouseX
        dragOffsetY = mouseY
    }

    /**
     * Updates the window position based on the current mouse position if dragging.
     * Should be called every frame while the mouse button is held.
     */
    fun updateDrag(mouseX: Double, mouseY: Double) {
        if (isDragging) {
            val winX = IntArray(1)
            val winY = IntArray(1)
            glfwGetWindowPos(glfwWindow, winX, winY)
            
            val deltaX = mouseX - dragOffsetX
            val deltaY = mouseY - dragOffsetY
            
            glfwSetWindowPos(glfwWindow, (winX[0] + deltaX).toInt(), (winY[0] + deltaY).toInt())
        }
    }

    /**
     * Stops the window dragging operation.
     */
    fun stopDrag() {
        isDragging = false
    }
}
