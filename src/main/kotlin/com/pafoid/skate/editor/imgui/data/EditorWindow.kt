package com.pafoid.skate.editor.imgui.data

import imgui.type.ImBoolean

/**
 * Data class combining window instance with its visibility flag and metadata.
 * Used by ImGuiLayer and EditorMenuBar for centralized window management.
 *
 * @param nameKey Localization key for window title (e.g., "window.hierarchy")
 * @param instance The window instance (must implement IWindow or IWindowWithScene)
 * @param showFlag Visibility toggle flag (persisted by ImGui)
 * @param requiresScene Whether imgui() needs Scene parameter
 */
data class EditorWindow(
    val nameKey: String,
    val instance: Any,
    val showFlag: ImBoolean,
    val requiresScene: Boolean = false
)