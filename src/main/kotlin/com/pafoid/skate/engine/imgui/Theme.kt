package com.pafoid.skate.engine.imgui

import com.pafoid.skate.engine.utils.Color
import org.joml.Vector4f

data class Theme(val text: Vector4f = Color.OFF_WHITE,
                 val disabledText: Vector4f = Color.GRAY,
                 val background: Vector4f = Color.SLATE,
                 val borders: Vector4f = Color.CHARCOAL,
)