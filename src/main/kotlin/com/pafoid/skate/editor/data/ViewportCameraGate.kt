package com.pafoid.skate.editor.data

/**
 * Decides which editor-camera inputs may act this frame (EDT-73). Fly keys and mouse look/orbit act only while the
 * Game Viewport window is focused; the wheel zooms only while it is hovered. Keys typed in any other window, or into
 * a text field, never move the camera.
 */
object ViewportCameraGate {
    /** WASD / up-down fly: the viewport is focused, no text field takes keys and Ctrl is up (Ctrl+S, Ctrl+D...). */
    fun flyAllowed(viewportFocused: Boolean, textInputActive: Boolean, ctrlDown: Boolean): Boolean =
        viewportFocused && !textInputActive && !ctrlDown

    /** Mouse look / orbit (middle drag): the viewport is focused and the pointer is over its image. */
    fun lookAllowed(viewportFocused: Boolean, pointerInsideImage: Boolean): Boolean =
        viewportFocused && pointerInsideImage

    /** Wheel zoom: the pointer is over the viewport image and the viewport window is the hovered one. */
    fun zoomAllowed(viewportHovered: Boolean, pointerInsideImage: Boolean): Boolean =
        viewportHovered && pointerInsideImage
}

/**
 * Game Viewport focus and hover as reported by its window each frame (EDT-73). The input handler [consume]s the
 * report, so a viewport that stops drawing (closed, hidden tab) reads as unfocused from the next frame on.
 */
class ViewportFocusReport {
    private var focused = false
    private var hovered = false

    /** Called by the Game Viewport window while it draws. */
    fun report(focused: Boolean, hovered: Boolean) {
        this.focused = focused
        this.hovered = hovered
    }

    /** Returns the last report as (focused, hovered) and clears it. */
    fun consume(): Pair<Boolean, Boolean> = (focused to hovered).also {
        focused = false
        hovered = false
    }
}
