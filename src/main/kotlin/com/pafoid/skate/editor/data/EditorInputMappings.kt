package com.pafoid.skate.editor.data

import com.pafoid.skate.engine.input.InputBinding
import kotlinx.serialization.Serializable
import org.lwjgl.glfw.GLFW

@Serializable
data class EditorInputMappings(
    var pause: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_P),
    var reset: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_R),

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

    fun resetToDefault() {
        // Simulation
        pause = InputBinding(keyboardKey = GLFW.GLFW_KEY_P)
        reset = InputBinding(keyboardKey = GLFW.GLFW_KEY_R)

        // Editor Camera
        moveForward = InputBinding(keyboardKey = GLFW.GLFW_KEY_W)
        moveBackward = InputBinding(keyboardKey = GLFW.GLFW_KEY_S)
        moveLeft = InputBinding(keyboardKey = GLFW.GLFW_KEY_A)
        moveRight = InputBinding(keyboardKey = GLFW.GLFW_KEY_D)

        // Gizmos
        gizmoSelect = InputBinding(keyboardKey = GLFW.GLFW_KEY_1)
        gizmoTranslate = InputBinding(keyboardKey = GLFW.GLFW_KEY_2)
        gizmoRotate = InputBinding(keyboardKey = GLFW.GLFW_KEY_3)
        gizmoScale = InputBinding(keyboardKey = GLFW.GLFW_KEY_4)
        measureTool = InputBinding(keyboardKey = GLFW.GLFW_KEY_M)
        deselectAll = InputBinding(keyboardKey = GLFW.GLFW_KEY_ESCAPE)

        // Hierarchy
        hierarchyCreateNew = InputBinding(keyboardKey = GLFW.GLFW_KEY_INSERT)
        hierarchyDelete = InputBinding(keyboardKey = GLFW.GLFW_KEY_DELETE)
        hierarchySelectFirst = InputBinding(keyboardKey = GLFW.GLFW_KEY_HOME)
        hierarchySelectLast = InputBinding(keyboardKey = GLFW.GLFW_KEY_END)
        hierarchyNavigateUp = InputBinding(keyboardKey = GLFW.GLFW_KEY_UP)
        hierarchyNavigateDown = InputBinding(keyboardKey = GLFW.GLFW_KEY_DOWN)
        hierarchyToggleVisibility = InputBinding(keyboardKey = GLFW.GLFW_KEY_V)
        hierarchyToggleLock = InputBinding(keyboardKey = GLFW.GLFW_KEY_L)
        hierarchyDuplicate = InputBinding(keyboardKey = GLFW.GLFW_KEY_D)
        hierarchyRename = InputBinding(keyboardKey = GLFW.GLFW_KEY_F2)
    }

}