package com.pafoid.skate.engine.controls.listeners

import com.pafoid.skate.engine.scenes.SceneManager
import imgui.ImGui
import org.joml.Vector2f
import org.joml.Vector4f
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
    private var mouseButtonPressedLastFrame = BooleanArray(9)
    private val gameViewportPos = Vector2f()
    private val gameViewportSize = Vector2f(1920f, 1080f) // Default size

    fun mousePosCallback(window: Long, xpos: Double, ypos: Double) {
        isDragging = mouseButtonsDown > 0
        lastX = xPos
        lastY = yPos
        lastWorldX = worldX
        lastWorldY = worldY
        xPos = xpos
        yPos = ypos
        
        val world = getWorld()
        worldX = world.x.toDouble()
        worldY = world.y.toDouble()
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

    fun getWorldDx(): Float {
        return (lastWorldX - worldX).toFloat()
    }

    fun getWorldDy(): Float {
        return (lastWorldY - worldY).toFloat()
    }

    fun getWorldX(): Float = worldX.toFloat()
    fun getWorldY(): Float = worldY.toFloat()

    fun getWorld(): Vector2f {
        var currentX: Float = getX() - gameViewportPos.x
        currentX = 2.0f * (currentX / gameViewportSize.x) - 1.0f
        var currentY: Float = getY() - gameViewportPos.y
        currentY = 2.0f * (1.0f - currentY / gameViewportSize.y) - 1

        val camera = SceneManager.getCurrentScene()?.camera ?: return Vector2f()
        val tmp = Vector4f(currentX, currentY, 0f, 1f)
        val inverseView = camera.getInverseView()
        val inverseProjection = camera.getInverseProjection()
        tmp.mul(inverseProjection.mul(inverseView))

        return Vector2f(tmp.x, tmp.y)
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
        val pickingX = ((relativeX / gameViewportSize.x) * 1920f).toInt().coerceIn(0, 1919)
        return pickingX.toFloat()
    }

    fun getScreenY(): Float {
        val mousePos = ImGui.getMousePos()
        val relativeY = mousePos.y - gameViewportPos.y
        val pickingY = ((relativeY / gameViewportSize.y) * 1080f).toInt().coerceIn(0, 1079)
        return pickingY.toFloat()
    }

    fun getScreenDx(): Float {
        return (getDx() / gameViewportSize.x) * 1920f
    }

    fun getScreenDy(): Float {
        return (getDy() / gameViewportSize.y) * 1080f
    }

    fun getDx(): Float = (xPos - lastX).toFloat()
    fun getDy(): Float = (yPos - lastY).toFloat()
    fun getScrollX(): Float = if (ImGui.getIO().wantCaptureMouse) 0f else scrollX.toFloat()
    fun getScrollY(): Float = if (ImGui.getIO().wantCaptureMouse) 0f else scrollY.toFloat()
    fun isDragging() = isDragging
    fun isMouseButtonDown(button: Int, ignoreImGui: Boolean = false): Boolean {
        val down = if (button < mouseButtonPressed.size) mouseButtonPressed[button] else false
        return if (ignoreImGui) down else down && !ImGui.getIO().wantCaptureMouse
    }

    fun getGameViewportSize(): Vector2f = gameViewportSize
    fun getGameViewportPos(): Vector2f = gameViewportPos
}