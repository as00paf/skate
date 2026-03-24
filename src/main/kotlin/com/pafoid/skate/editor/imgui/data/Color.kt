package com.pafoid.skate.editor.imgui.data

import org.joml.Vector4f

object Color {

    // Default colors
    val TRANSPARENT = Vector4f(0f, 0f, 0f, 0f)
    val WHITE = Vector4f(1f, 1f, 1f, 1f)
    val BLACK = Vector4f(0f, 0f, 0f, 1f)
    val GRAY = Vector4f(0.6f, 0.6f, 0.6f, 1f)
    val DARK_GRAY = Vector4f(0.3f, 0.3f, 0.3f, 1f)
    val RED = Vector4f(1f, 0f, 0f, 1f)
    val GREEN = Vector4f(0f, 1f, 0f, 1f)
    val BLUE = Vector4f(0f, 0f, 1f, 1f)
    val YELLOW = Vector4f(1f, 1f, 0f, 1f)

    // Style colors
    val OFF_WHITE = Vector4f(.9f, .9f, .9f, 1f)
    val SLATE = Vector4f(0.13f, 0.14f, 0.17f, 1f)
    val CHARCOAL = Vector4f(0.26f, 0.26f, 0.26f, 1f)

    // Islands Dark Theme Colors
    val ISLAND_BACKGROUND = Vector4f(0.11f, 0.12f, 0.15f, 1f) // Darker Slate
    val ISLAND_WIDGET_BG = Vector4f(0.16f, 0.17f, 0.20f, 1f)   // Lighter than background
    val ISLAND_WIDGET_HOVER = Vector4f(0.20f, 0.21f, 0.24f, 1f)
    val ISLAND_WIDGET_ACTIVE = Vector4f(0.23f, 0.24f, 0.27f, 1f)
    val ISLAND_ACCENT_BLUE = Vector4f(0.26f, 0.59f, 0.98f, 1f) // Classic ImGui accent blue
    val ISLAND_ACCENT_HOVER = Vector4f(0.36f, 0.69f, 1.00f, 1f)
    val ISLAND_BORDER = Vector4f(0.23f, 0.24f, 0.27f, 1f)     // Subtle border
    val ISLAND_HEADER = Vector4f(0.18f, 0.19f, 0.22f, 1f)
    val ISLAND_HEADER_HOVER = Vector4f(0.23f, 0.24f, 0.27f, 1f)
    val ISLAND_TAB_INACTIVE = Vector4f(0.13f, 0.14f, 0.17f, 1f)
    val ISLAND_TAB_ACTIVE = Vector4f(0.16f, 0.17f, 0.20f, 1f)
    val ISLAND_TAB_HOVER = Vector4f(0.20f, 0.21f, 0.24f, 1f)
    val ISLAND_SCROLLBAR_GRAB_HOVER = Vector4f(0.33f, 0.34f, 0.37f, 1f)
    val ISLAND_SCROLLBAR_GRAB_ACTIVE = Vector4f(0.43f, 0.44f, 0.47f, 1f)
    val ISLAND_TEXT = Vector4f(0.85f, 0.85f, 0.85f, 1f)
    val ISLAND_TEXT_DIM = Vector4f(0.55f, 0.55f, 0.55f, 1f)

    // UI Feedback & Overlay
    val ISLAND_SELECTION = Vector4f(0.26f, 0.59f, 0.98f, 0.35f)
    val ISLAND_NAV_WINDOWING_HIGHLIGHT = Vector4f(1.00f, 1.00f, 1.00f, 0.70f)
    val ISLAND_NAV_WINDOWING_DIM_BG = Vector4f(0.80f, 0.80f, 0.80f, 0.20f)
    val ISLAND_MODAL_DIM_BG = Vector4f(0.00f, 0.00f, 0.00f, 0.60f)
}
