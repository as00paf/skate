package com.pafoid.skate.engine.input.listeners

import com.pafoid.skate.engine.input.PointerSource
import com.pafoid.skate.engine.input.ViewRect
import com.pafoid.skate.engine.input.ViewportPointerMapping
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE

class MouseListener {
    /** True while an overlay UI owns the mouse (the editor installs ImGui's `wantCaptureMouse`); see KeyListener (GUI-14). */
    var captureMouse: () -> Boolean = { false }

    private var scrollX: Double = 0.0
    private var scrollY: Double = 0.0
    private var xPos: Double = 0.0
    private var yPos: Double = 0.0
    private var lastX: Double = 0.0
    private var lastY: Double = 0.0
    /** GLFW cursor motion this frame in window (screen) units, unmapped; also valid with a captured cursor. */
    var dx: Float = 0f
    var dy: Float = 0f
    private var isDragging = false
    private var mouseButtonsDown = 0
    private var mouseButtonPressed = BooleanArray(9)
    private var mouseButtonPressedLastFrame = BooleanArray(9)

    /** Per button: whether the current press started inside [viewRect] ([PRESS_UNKNOWN] until first resolved). */
    private val viewPress = ByteArray(9)

    /**
     * The single pointer source for everything compared with [viewRect] (contract: docs/design/INP-09.md). Defaults to the GLFW cursor (window
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
            if (button in mouseButtonPressed.indices) {
                mouseButtonPressed[button] = true
                viewPress[button] = PRESS_UNKNOWN
            }
        } else if (action == GLFW_RELEASE) {
            mouseButtonsDown = (mouseButtonsDown - 1).coerceAtLeast(0)
            if (button in mouseButtonPressed.indices) {
                mouseButtonPressed[button] = false
                viewPress[button] = PRESS_UNKNOWN
                isDragging = false
            }
        }
    }

    fun mouseScrollCallback(window: Long, offsetX: Double, offsetY: Double) {
        scrollX += offsetX // accumulate: several wheel ticks can arrive in one (slow) frame
        scrollY += offsetY
    }

    fun endFrame() {
        scrollX = 0.0
        scrollY = 0.0
        lastX = xPos
        lastY = yPos
        dx = 0f
        dy = 0f
        // Resolve presses nobody queried this frame while the pointer is still where the press happened.
        for (b in mouseButtonPressed.indices) if (mouseButtonPressed[b]) pressStartedInView(b)
        System.arraycopy(mouseButtonPressed, 0, mouseButtonPressedLastFrame, 0, mouseButtonPressed.size)
    }
    
    fun mouseButtonBeginPress(button: Int): Boolean {
        if (button !in mouseButtonPressed.indices) return false
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
        viewPress.fill(PRESS_UNKNOWN)
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

    /**
     * Game-view button: down while [button] is held and its press started inside [viewRect]. It stays down when the
     * pointer leaves the view mid-drag, and a press started elsewhere (an editor panel) never reaches the game.
     * Independent of ImGui capture, which is always set over the editor's viewport window.
     */
    fun isViewButtonDown(button: Int): Boolean =
        button in mouseButtonPressed.indices && mouseButtonPressed[button] && pressStartedInView(button)

    /** First frame of a game-view press (see [isViewButtonDown]). */
    fun viewButtonBeginPress(button: Int): Boolean = mouseButtonBeginPress(button) && pressStartedInView(button)

    /** Resolved lazily (first query or [endFrame] of the press frame), so GLFW callbacks never read the pointer source. */
    private fun pressStartedInView(button: Int): Boolean {
        if (viewPress[button] == PRESS_UNKNOWN) viewPress[button] = if (isInsideView()) PRESS_IN_VIEW else PRESS_OUTSIDE
        return viewPress[button] == PRESS_IN_VIEW
    }

    /** This frame's motion in render-target pixels. */
    fun deltaTarget(): Pair<Float, Float> = ViewportPointerMapping.deltaToTarget(dx, dy, viewRect, targetWidth, targetHeight)

    private fun readPointer(): FloatArray = pointer.also { pointerSource.read(it) }

    fun getScrollX(): Float = scrollX.toFloat()// TODO: stop with the conversions
    fun getScrollY(): Float = scrollY.toFloat()

    fun isDragging() = isDragging
    fun isMouseButtonDown(button: Int, ignoreImGui: Boolean = false): Boolean {
        val down = button in mouseButtonPressed.indices && mouseButtonPressed[button]
        return if (ignoreImGui) down else down && !captureMouse()
    }

    private companion object {
        const val PRESS_UNKNOWN: Byte = 0
        const val PRESS_IN_VIEW: Byte = 1
        const val PRESS_OUTSIDE: Byte = 2
    }
}
