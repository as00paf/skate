package com.pafoid.skate.editor.data

import com.pafoid.skate.engine.input.InputBinding
import kotlinx.serialization.Serializable
import org.lwjgl.glfw.GLFW

/** The editor's rebindable keys; [EditorShortcuts] describes each one (label, modifiers, scope). */
@Serializable
data class EditorInputMappings(
    var playToggle: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_F5),
    var cameraReset: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_HOME),
    var focusSelected: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_F),

    // EditorCamera
    var moveForward: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_W),
    var moveBackward: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_S),
    var moveLeft: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_A),
    var moveRight: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_D),
    var moveUp: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_SPACE),
    var moveDown: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_LEFT_SHIFT),

    // Gizmos
    var gizmoSelect: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_1),
    var gizmoTranslate: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_2),
    var gizmoRotate: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_3),
    var gizmoScale: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_4),
    var deselectAll: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_ESCAPE),
    var measureTool: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_M),

    // Hierarchy actions
    var hierarchyCreateNew: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_INSERT),
    var hierarchyDelete: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_DELETE),
    var hierarchySelectFirst: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_HOME),
    var hierarchySelectLast: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_END),
    var hierarchyNavigateUp: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_UP),
    var hierarchyNavigateDown: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_DOWN),
    var hierarchyToggleVisibility: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_V),
    var hierarchyToggleLock: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_L),
    var hierarchyDuplicate: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_D),
    var hierarchyRename: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_F2),

    // Editor shortcuts
    var openSearchWindow: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_P),
    var toggleFullScreen: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_F12),
) {

    /** Every binding back to its default (all of them, from the constructor defaults). */
    fun resetToDefault() {
        val defaults = EditorInputMappings()
        EditorShortcuts.rebindable.forEach { shortcut ->
            val binding = shortcut.binding ?: return@forEach
            binding.set(this, binding.get(defaults).copy())
        }
    }
}
