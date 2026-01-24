package com.pafoid.skate.engine.controls

import org.joml.Vector2f
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE


object MouseListener {
    private var scrollX: Double = 0.0
    private var scrollY: Double = 0.0
    private var xPos: Double = 0.0
    private var yPos: Double = 0.0
    private var lastX: Double = 0.0
    private var lastY: Double = 0.0
    private var worldX: Double = 0.0
    private var worldY: Double = 0.0
    private var lastWorldX: Double = 0.0
    private var lastWorldY: Double = 0.0
    private var isDragging = false
    private var mouseButtonsDown = 0
    private var mouseButtonPressed = BooleanArray(9)
    private val gameViewportPos = Vector2f()
    private val gameViewportSize = Vector2f()

    fun mousePosCallback(window: Long, xpos: Double, ypos: Double) {
        isDragging = mouseButtonsDown > 0
        lastX = xPos
        lastY = yPos
        lastWorldX = worldX
        lastWorldY = worldY
        xPos = xpos
        yPos = ypos
    }

    fun mouseButtonCallback(window: Long, button: Int, action: Int, mods: Int) {
        if (action == GLFW_PRESS) {
            mouseButtonsDown++
            mouseButtonPressed[button] = true
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

    fun getWorldDx(): Float {
        return (lastWorldX - worldX).toFloat()
    }

    fun getWorldDy(): Float {
        return (lastWorldY - worldY).toFloat()
    }

    fun setGameViewportPos(pos: Vector2f) {
        gameViewportPos.set(pos)
    }

    fun setGameViewportSize(size: Vector2f) {
        gameViewportSize.set(size)
    }

    fun getScreenX(): Float {
        return getScreen().x
    }

    fun getScreenY(): Float {
        return getScreen().y
    }

    fun getScreen(): Vector2f {
        var currentX: Float = getX() - gameViewportPos.x
        currentX = currentX / gameViewportSize.x * 1920.0f
        var currentY: Float = getY() - gameViewportPos.y
        currentY = (1.0f - currentY / gameViewportSize.y) * 1080.0f
        return Vector2f(currentX, currentY)
    }

    fun getX(): Float = xPos.toFloat()
    fun getY(): Float = yPos.toFloat()
    fun getDx(): Float = (xPos - lastX).toFloat()
    fun getDy(): Float = (yPos - lastY).toFloat()
    fun getScrollX(): Float = if (imgui.ImGui.getIO().wantCaptureMouse) 0f else scrollX.toFloat()
    fun getScrollY(): Float = if (imgui.ImGui.getIO().wantCaptureMouse) 0f else scrollY.toFloat()
    fun isDragging() = isDragging && !imgui.ImGui.getIO().wantCaptureMouse
    fun isMouseButtonDown(button: Int): Boolean {
        return if (button < mouseButtonPressed.size) mouseButtonPressed[button] && !imgui.ImGui.getIO().wantCaptureMouse
        else false
    }
}