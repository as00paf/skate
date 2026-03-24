package com.pafoid.skate.editor.imgui.data

import org.joml.Vector4f

data class Theme(
    val text: Vector4f = Color.ISLAND_TEXT,
    val textDisabled: Vector4f = Color.ISLAND_TEXT_DIM,
    val background: Vector4f = Color.ISLAND_BACKGROUND,
    val border: Vector4f = Color.ISLAND_BORDER,
    val borderShadow: Vector4f = Color.TRANSPARENT,
    val accent: Vector4f = Color.ISLAND_ACCENT_BLUE,
    val accentHover: Vector4f = Color.ISLAND_ACCENT_HOVER,
    val widgetBg: Vector4f = Color.ISLAND_WIDGET_BG,
    val widgetHover: Vector4f = Color.ISLAND_WIDGET_HOVER,
    val widgetActive: Vector4f = Color.ISLAND_WIDGET_ACTIVE,
    val header: Vector4f = Color.ISLAND_HEADER,
    val headerHover: Vector4f = Color.ISLAND_HEADER_HOVER,
    val tabActive: Vector4f = Color.ISLAND_TAB_ACTIVE,
    val tabHover: Vector4f = Color.ISLAND_TAB_HOVER,
    val tabInactive: Vector4f = Color.ISLAND_TAB_INACTIVE,
    val scrollbarGrab: Vector4f = Color.ISLAND_BORDER,
    val scrollbarGrabHover: Vector4f = Color.ISLAND_SCROLLBAR_GRAB_HOVER,
    val scrollbarGrabActive: Vector4f = Color.ISLAND_SCROLLBAR_GRAB_ACTIVE,
    val selection: Vector4f = Color.ISLAND_SELECTION,
    val navHighlight: Vector4f = Color.ISLAND_ACCENT_BLUE,
    val navWindowingHighlight: Vector4f = Color.ISLAND_NAV_WINDOWING_HIGHLIGHT,
    val navWindowingDimBg: Vector4f = Color.ISLAND_NAV_WINDOWING_DIM_BG,
    val modalDimBg: Vector4f = Color.ISLAND_MODAL_DIM_BG
)
