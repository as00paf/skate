package com.pafoid.skate.editor.ui.windows

/**
 * Auto-scroll state of the console log list (EDT-47), kept free of ImGui so it can be unit-tested.
 *
 * Call [update] once per frame per scroll region, inside the child window, with the region's current
 * scroll offset and maximum. Rules:
 * - the user scrolling up (offset decreased while not at the bottom) turns auto-scroll off;
 * - the user scrolling back down to the bottom turns it on again;
 * - new content arriving (maximum grows, offset unchanged) never changes the state;
 * - the offset being clamped down because content shrank (clear, filter) lands at the bottom and is not a user scroll.
 *
 * The enabled flag is shared by all regions (one checkbox); the previous offset is tracked per region.
 */
class ConsoleAutoScroll(enabled: Boolean = true) {
    var enabled: Boolean = enabled
        private set

    private val lastScrollY = HashMap<String, Float>()

    /** Explicit toggle from the checkbox. */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /**
     * Updates the state from this frame's scroll position and returns `true` when the caller should
     * pin the view to the bottom this frame. [hold] suppresses pinning (e.g. while lines are selected)
     * without changing the state.
     */
    fun update(region: String, scrollY: Float, scrollMaxY: Float, hold: Boolean = false): Boolean {
        val atBottom = scrollY >= scrollMaxY - BOTTOM_TOLERANCE
        val previous = lastScrollY.put(region, scrollY)
        if (previous != null) {
            if (enabled && scrollY < previous && !atBottom) {
                enabled = false
            } else if (!enabled && scrollY > previous && atBottom) {
                enabled = true
            }
        }
        return enabled && !hold
    }

    companion object {
        /** Pixels from the end that still count as "at the bottom". */
        const val BOTTOM_TOLERANCE = 1f
    }
}
