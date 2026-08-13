package com.pafoid.skate.editor.imgui

import imgui.type.ImBoolean

abstract class EditorWindow(
    val name: String,
    val isDefault: Boolean = false,
    val isOpen: ImBoolean = ImBoolean(false)
) {
    abstract fun imgui()
}