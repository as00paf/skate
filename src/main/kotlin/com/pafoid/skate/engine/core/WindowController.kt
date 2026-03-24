package com.pafoid.skate.engine.core

import org.lwjgl.glfw.GLFW.*

/**
 * Handles custom window operations for a GLFW window without decorations.
 * Provides functionality for minimizing, maximizing, restoring, closing, and dragging the window.
 */
class WindowController(private val glfwWindow: Long) {

    private var isDragging = false
    private var dragOffsetX = 0.0
    private var dragOffsetY = 0.0

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
        glfwMaximizeWindow(glfwWindow)
    }

    /**
     * Restores the window from a maximized or minimized state.
     */
    fun restore() {
        glfwRestoreWindow(glfwWindow)
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
