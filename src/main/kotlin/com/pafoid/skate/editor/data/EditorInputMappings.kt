package com.pafoid.skate.editor.data

import com.pafoid.skate.engine.input.InputBinding
import kotlinx.serialization.Serializable
import org.lwjgl.glfw.GLFW

@Serializable
data class EditorInputMappings(
    var gizmoTranslate: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_W),
    var gizmoRotate: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_E),
    var gizmoScale: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_R),
    var gizmoSelect: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_Q),
    var deselectAll: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_ESCAPE),
    var measureTool: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_M)
) {
}