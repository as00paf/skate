package com.pafoid.skate.editor.imgui.data

/**
 * Centralized constants for UI layout values used across the editor.
 *
 * Using these constants ensures consistency between components that need
 * to agree on sizes (e.g., dockspace layout reserving space for the status bar).
 */
object UiConstants {

    // -- Layout heights --
    const val STATUS_BAR_HEIGHT = 30f
    const val DEFAULT_BUTTON_HEIGHT = 30f

    // -- Spacing --
    const val SECTION_SPACING = 10f

    // -- Window defaults --
    const val DIALOG_WIDTH = 550f
    const val DIALOG_HEIGHT = 440f

    // -- Viewport Toolbar --
    const val TOOLBAR_HEIGHT = 40f
    const val TOOLBAR_BUTTON_HEIGHT = 30f
    const val TOOLBAR_BUTTON_SPACING = 10f
    const val SEPARATOR_SPACING = 18f
    const val SEPARATOR_WIDTH = 8f
}
