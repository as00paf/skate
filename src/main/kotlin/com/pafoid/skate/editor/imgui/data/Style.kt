package com.pafoid.skate.editor.imgui.data

import org.joml.Vector2f

data class Style(
    val rounding: Float = 6f,
    val windowPadding: Vector2f = Vector2f(8f, 8f),
    val framePadding: Vector2f = Vector2f(5f, 3f),
    val itemSpacing: Vector2f = Vector2f(8f, 4f),
    val itemInnerSpacing: Vector2f = Vector2f(4f, 4f),
    val touchExtraPadding: Vector2f = Vector2f(0f, 0f),
    val indentSpacing: Float = 21f,
    val scrollbarSize: Float = 14f,
    val grabMinSize: Float = 10f,
    val theme: Theme = Theme()
)
