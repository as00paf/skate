package com.pafoid.skate.engine.core

import org.lwjgl.glfw.GLFW.GLFW_MAXIMIZED
import org.lwjgl.glfw.GLFW.GLFW_TRUE
import org.lwjgl.glfw.GLFW.glfwGetMonitorPos
import org.lwjgl.glfw.GLFW.glfwGetMonitorWorkarea
import org.lwjgl.glfw.GLFW.glfwGetMonitors
import org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor
import org.lwjgl.glfw.GLFW.glfwGetVideoMode
import org.lwjgl.glfw.GLFW.glfwGetWindowAttrib
import org.lwjgl.glfw.GLFW.glfwGetWindowPos
import org.lwjgl.glfw.GLFW.glfwGetWindowSize
import org.lwjgl.glfw.GLFW.glfwIconifyWindow
import org.lwjgl.glfw.GLFW.glfwMaximizeWindow
import org.lwjgl.glfw.GLFW.glfwRestoreWindow
import org.lwjgl.glfw.GLFW.glfwSetWindowMonitor
import org.lwjgl.glfw.GLFW.glfwSetWindowPos
import org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose
import org.lwjgl.glfw.GLFW.glfwSetWindowSize
import org.lwjgl.glfw.GLFW.glfwSwapInterval
import org.lwjgl.system.MemoryUtil.NULL

/**
 * Handles custom window operations.
 * Provides functionality for minimizing, maximizing, restoring, and closing the window.
 */
class WindowController(val glfwWindow: Long) {

    var isFixingMaximize = false

    // Store window size before maximizing for proper restore
    private var preMaximizeWidth = 1920
    private var preMaximizeHeight = 1080
    private var preMaximizeX = 0
    private var preMaximizeY = 10

    var isLogicallyMaximized = false
        private set

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

        isLogicallyMaximized = true
        glfwMaximizeWindow(glfwWindow)
    }

    /**
     * Restores the window.
     */
    fun restore() {
        isLogicallyMaximized = false
        glfwRestoreWindow(glfwWindow)
        glfwSetWindowPos(glfwWindow, preMaximizeX, preMaximizeY)
        glfwSetWindowSize(glfwWindow, preMaximizeWidth, preMaximizeHeight)
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
        return isLogicallyMaximized || glfwGetWindowAttrib(glfwWindow, GLFW_MAXIMIZED) == GLFW_TRUE
    }

    /**
     * Called by the maximize callback to sync native OS maximize events.
     */
    fun setLogicallyMaximized(maximized: Boolean) {
        this.isLogicallyMaximized = maximized
    }

    /**
     * Fixes the window bounds when maximized without decorations, preventing gaps.
     */
    fun fixMaximizeBounds() {
        val monitor = getCurrentMonitor()
        val mx = IntArray(1)
        val my = IntArray(1)
        val mw = IntArray(1)
        val mh = IntArray(1)
        glfwGetMonitorWorkarea(monitor, mx, my, mw, mh)
        glfwSetWindowPos(glfwWindow, mx[0], my[0])
        glfwSetWindowSize(glfwWindow, mw[0], mh[0])
    }

    private fun getCurrentMonitor(): Long {
        val wx = IntArray(1)
        val wy = IntArray(1)
        val ww = IntArray(1)
        val wh = IntArray(1)
        glfwGetWindowPos(glfwWindow, wx, wy)
        glfwGetWindowSize(glfwWindow, ww, wh)
        val cx = wx[0] + ww[0] / 2
        val cy = wy[0] + wh[0] / 2

        val monitors = glfwGetMonitors() ?: return glfwGetPrimaryMonitor()
        for (i in 0 until monitors.capacity()) {
            val monitor = monitors[i]
            val mx = IntArray(1)
            val my = IntArray(1)
            glfwGetMonitorPos(monitor, mx, my)
            val mode = glfwGetVideoMode(monitor) ?: continue
            if (cx >= mx[0] && cx <= mx[0] + mode.width() && cy >= my[0] && cy <= my[0] + mode.height()) {
                return monitor
            }
        }
        return glfwGetPrimaryMonitor()
    }

    fun setFullscreen(enabled: Boolean) {
        val monitor = glfwGetPrimaryMonitor()
        val vidMode = glfwGetVideoMode(monitor) ?: return
        if (enabled) {
            glfwSetWindowMonitor(glfwWindow, monitor, 0, 0, vidMode.width(), vidMode.height(), vidMode.refreshRate())
        } else {
            // TODO: check if still works
            glfwSetWindowMonitor(glfwWindow, NULL, 100, 100, vidMode.width(), vidMode.height(), vidMode.refreshRate())
        }
    }

    fun setVSync(enabled: Boolean) {
        glfwSwapInterval(if (enabled) 1 else 0)
    }
}

