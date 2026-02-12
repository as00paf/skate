package com.pafoid.skate.editor.imgui.data

import org.joml.Vector4f

data class Theme(val text: Vector4f = Color.OFF_WHITE,
                 val disabledText: Vector4f = Color.GRAY,
                 val background: Vector4f = Color.SLATE,
                 val borders: Vector4f = Color.CHARCOAL,
)