package com.pafoid.skate.engine.input.listeners

import imgui.ImGui
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE

class MouseListener {
    private var scrollX: Double = 0.0
    private var scrollY: Double = 0.0
    private var xPos: Double = 0.0
    private var yPos: Double = 0.0
    private var lastX: Double = 0.0
    private var lastY: Double = 0.0
    var dx: Float = 0f
    var dy: Float = 0f
    private var isDragging = false
    private var mouseButtonsDown = 0
    private var mouseButtonPressed = BooleanArray(9)
    private var mouseButtonPressedLastFrame = BooleanArray(9)
    val gameViewportPos = Vector2f()
    val gameViewportSize = Vector2f(1920f, 1080f) // Default size

    fun mousePosCallback(window: Long, xpos: Double, ypos: Double) {
        isDragging = mouseButtonsDown > 0
        lastX = xPos
        lastY = yPos
        xPos = xpos
        yPos = ypos
        dx = xpos.toFloat() - lastX.toFloat()
        dy = ypos.toFloat() - lastY.toFloat()
    }

    fun mouseButtonCallback(window: Long, button: Int, action: Int, mods: Int) {
        if (action == GLFW_PRESS) {
            mouseButtonsDown++
            if (button < mouseButtonPressed.size) {
                mouseButtonPressed[button] = true
            }
        } else if (action == GLFW_RELEASE) {
            mouseButtonsDown--
            if (button < mouseButtonPressed.size) {
                mouseButtonPressed[button] = false
                isDragging = false
            }
        }
    }

    fun mouseScrollCallback(window: Long, offsetX: Double, offsetY: Double) {
        scrollX = offsetX
        scrollY = offsetY
    }

    fun endFrame() {
        scrollX = 0.0
        scrollY = 0.0
        lastX = xPos
        lastY = yPos
        System.arraycopy(mouseButtonPressed, 0, mouseButtonPressedLastFrame, 0, mouseButtonPressed.size)
    }
    
    fun mouseButtonBeginPress(button: Int): Boolean {
        if (button >= mouseButtonPressed.size) return false
        return mouseButtonPressed[button] && !mouseButtonPressedLastFrame[button]
    }

    fun clear() {
        scrollX = 0.0
        scrollY = 0.0
        xPos = 0.0
        yPos = 0.0
        lastX = 0.0
        lastY = 0.0
        mouseButtonsDown = 0
        isDragging = false
        mouseButtonPressed.fill(false)
    }

    fun setGameViewportPos(pos: Vector2f) {
        gameViewportPos.set(pos)
    }

    fun setGameViewportSize(size: Vector2f) {
        gameViewportSize.set(size)
    }

    fun getX(): Float = xPos.toFloat()
    fun getY(): Float = yPos.toFloat()

    fun getScreenX(): Float {
        val mousePos = ImGui.getMousePos()
        val relativeX = mousePos.x - gameViewportPos.x
        return relativeX.coerceIn(0f, gameViewportSize.x)
    }

    fun getScreenY(): Float {
        val mousePos = ImGui.getMousePos()
        val relativeY = mousePos.y - gameViewportPos.y
        return relativeY.coerceIn(0f, gameViewportSize.y)
    }

    fun getScrollX(): Float = if (ImGui.getIO().wantCaptureMouse) 0f else scrollX.toFloat()
    fun getScrollY(): Float = if (ImGui.getIO().wantCaptureMouse) 0f else scrollY.toFloat()
    fun isDragging() = isDragging
    fun isMouseButtonDown(button: Int, ignoreImGui: Boolean = false): Boolean {
        val down = if (button < mouseButtonPressed.size) mouseButtonPressed[button] else false
        return if (ignoreImGui) down else down && !ImGui.getIO().wantCaptureMouse
    }


}
