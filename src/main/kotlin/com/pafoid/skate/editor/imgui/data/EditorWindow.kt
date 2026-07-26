package com.pafoid.skate.editor.imgui.data

import imgui.type.ImBoolean

data class EditorWindow(
    val nameKey: String,
    val instance: Any,
    val showFlag: ImBoolean = ImBoolean(false),
    val requiresScene: Boolean = false
)