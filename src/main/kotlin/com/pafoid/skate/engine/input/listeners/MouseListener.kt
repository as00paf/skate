package com.pafoid.skate.engine.input.listeners

import com.pafoid.skate.engine.input.PointerSource
import com.pafoid.skate.engine.input.ViewRect
import com.pafoid.skate.engine.input.ViewportPointerMapping
import imgui.ImGui
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

    /**
     * The single pointer source for everything compared with [viewRect]. Defaults to the GLFW cursor (window
     * coordinates), which matches the game where the view fills the window. The editor swaps in ImGui's mouse,
     * whose desktop coordinates match the viewport image rect even with multi-viewports.
     */
    var pointerSource: PointerSource = PointerSource { out -> out[0] = xPos.toFloat(); out[1] = yPos.toFloat() }

    /** Where the game view is shown, in [pointerSource] units. */
    var viewRect: ViewRect = ViewRect(0f, 0f, 1920f, 1080f)
        private set

    /** Pixel size of the render target displayed in [viewRect]. */
    var targetWidth: Int = 1920
        private set
    var targetHeight: Int = 1080
        private set

    private val pointer = FloatArray(2)

    fun mousePosCallback(window: Long, xpos: Double, ypos: Double) {
        isDragging = mouseButtonsDown > 0
        lastX = xPos
        lastY = yPos
        xPos = xpos
        yPos = ypos
        if (skipNextDelta) {
            skipNextDelta = false
            return
        }
        dx += xpos.toFloat() - lastX.toFloat()
        dy += ypos.toFloat() - lastY.toFloat()
    }

    private var skipNextDelta = false

    fun discardNextDelta() {
        skipNextDelta = true
    }

    fun mouseButtonCallback(window: Long, button: Int, action: Int, mods: Int) {
        if (action == GLFW_PRESS) {
            mouseButtonsDown++
            if (button < mouseButtonPressed.size) {
                mouseButtonPressed[button] = true
            }
        } else if (action == GLFW_RELEASE) {
            mouseButtonsDown = (mouseButtonsDown - 1).coerceAtLeast(0)
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
        dx = 0f
        dy = 0f
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
        dx = 0f
        dy = 0f
        mouseButtonPressed.fill(false)
    }

    fun setViewRect(rect: ViewRect) {
        if (!rect.isEmpty) viewRect = rect
    }

    fun setTargetSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            targetWidth = width
            targetHeight = height
        }
    }

    /** Raw GLFW cursor, window coordinates. */
    fun getX(): Float = xPos.toFloat()
    fun getY(): Float = yPos.toFloat()

    /** Pointer in [pointerSource] units (the space of [viewRect]). */
    fun pointerX(): Float = readPointer()[0]
    fun pointerY(): Float = readPointer()[1]

    fun isInsideView(): Boolean = readPointer().let { ViewportPointerMapping.contains(it[0], it[1], viewRect) }

    /** Offset from the view's top-left in pointer units, clamped to the view. */
    fun viewLocal(): Pair<Float, Float> = readPointer().let { ViewportPointerMapping.local(it[0], it[1], viewRect) }

    /** Fraction of the view in [0, 1]. */
    fun viewNormalized(): Pair<Float, Float> =
        readPointer().let { ViewportPointerMapping.normalized(it[0], it[1], viewRect) }

    /** Render-target pixels, not clamped. */
    fun viewTarget(): Pair<Float, Float> =
        readPointer().let { ViewportPointerMapping.toTarget(it[0], it[1], viewRect, targetWidth, targetHeight) }

    fun viewNdc(): Pair<Float, Float> = readPointer().let { ViewportPointerMapping.toNdc(it[0], it[1], viewRect) }

    /** This frame's motion in render-target pixels. */
    fun deltaTarget(): Pair<Float, Float> = ViewportPointerMapping.deltaToTarget(dx, dy, viewRect, targetWidth, targetHeight)

    private fun readPointer(): FloatArray = pointer.also { pointerSource.read(it) }

    fun getScrollX(): Float = scrollX.toFloat()// TODO: stop with the conversions
    fun getScrollY(): Float = scrollY.toFloat()

    fun isDragging() = isDragging
    fun isMouseButtonDown(button: Int, ignoreImGui: Boolean = false): Boolean {
        val down = if (button < mouseButtonPressed.size) mouseButtonPressed[button] else false
        return if (ignoreImGui) down else down && !ImGui.getIO().wantCaptureMouse
    }
}
