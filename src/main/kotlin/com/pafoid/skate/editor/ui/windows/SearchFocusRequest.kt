package com.pafoid.skate.editor.ui.windows

/**
 * One-shot keyboard-focus request for the Search Anywhere input (EDT-48), kept free of ImGui so it can be unit-tested.
 *
 * Focus is requested once when the window opens (or after the clear button). It must not be re-requested every frame:
 * that keeps taking focus back and stops clicks into the field from sticking.
 */
class SearchFocusRequest {
    private var isShown = false
    private var pending = false

    /** Call at the start of every frame the window is drawn. Returns `true` on the first frame after it opened. */
    fun beginFrame(): Boolean {
        if (isShown) return false
        isShown = true
        pending = true
        return true
    }

    /** Call when the window closes, so the next open counts as a new open. */
    fun onClosed() {
        isShown = false
        pending = false
    }

    /** Asks for focus again on the next input render (e.g. after clearing the query). */
    fun request() {
        pending = true
    }

    /** Returns `true` once per request; call right before rendering the input. */
    fun consume(): Boolean {
        val result = pending
        pending = false
        return result
    }
}
