package com.pafoid.skate.editor.imgui.theme

import com.pafoid.skate.editor.imgui.data.Theme
import org.joml.Vector4f

/**
 * The colours of the theme applied to ImGui (set by `ImGuiStyleManager.setupStyle`), for drawing that has no ImGui
 * style slot: status text (success, warning, danger), diff lines, graph lanes (EDT-79). Read it each frame, so a theme
 * switch shows at once.
 */
object ThemeColors {
    @Volatile
    var current: Theme = Theme()

    val text: Vector4f get() = current.text
    val textDisabled: Vector4f get() = current.textDisabled
    val accent: Vector4f get() = current.accent
    val accentHover: Vector4f get() = current.accentHover
    val success: Vector4f get() = current.success
    val warning: Vector4f get() = current.warning
    val danger: Vector4f get() = current.danger
}
