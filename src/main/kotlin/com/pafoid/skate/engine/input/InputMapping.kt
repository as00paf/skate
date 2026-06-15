package com.pafoid.skate.engine.input

import kotlinx.serialization.Serializable
import org.lwjgl.glfw.GLFW

@Serializable
class InputMappings(
    var moveUp: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_W, gamepadAxis = 1),  // AXIS_LEFT_Y
    var moveDown: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_S, gamepadAxis = 1),  // AXIS_LEFT_Y
    var moveLeft: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_A, gamepadAxis = 0),  // AXIS_LEFT_X
    var moveRight: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_D, gamepadAxis = 0),  // AXIS_LEFT_X
    var sprint: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_LEFT_SHIFT,
        gamepadAxis = 4
    ),  // AXIS_LEFT_TRIGGER
    var crouch: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_LEFT_CONTROL, gamepadButton = 4),  // BUTTON_LB
    var jump: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_SPACE, gamepadButton = 0),  // BUTTON_A
    var flipLeft: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_Q, gamepadButton = 4),  // BUTTON_LB
    var flipRight: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_E, gamepadButton = 5),  // BUTTON_RB
    var kickflip: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_W, gamepadButton = 2),  // BUTTON_X
    var heelflip: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_S, gamepadButton = 3),  // BUTTON_Y
    var grab: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_SPACE,
        gamepadButton = 0
    ),  // BUTTON_A (same as jump, context-sensitive)
    var manual: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_LEFT_ALT, gamepadButton = 6),  // BUTTON_BACK
    var cameraLookX: InputBinding = InputBinding(gamepadAxis = 2),  // AXIS_RIGHT_X
    var cameraLookY: InputBinding = InputBinding(gamepadAxis = 3),  // AXIS_RIGHT_Y
    var cameraReset: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_R, gamepadButton = 9),  // BUTTON_RS
    var pause: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_ESCAPE, gamepadButton = 7),  // BUTTON_START
    var reset: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_DELETE,
        gamepadButton = 6
    ),  // BUTTON_BACK (combination handled in InputSystem)
    var stanceChange: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_LEFT,
        gamepadButton = 13
    ),  // BUTTON_DPAD_LEFT
    var stanceChangeRight: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_RIGHT,
        gamepadButton = 11
    ),  // BUTTON_DPAD_RIGHT

    // TODO: move editor stuff
    var editorGizmoTranslate: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_W),
    var editorGizmoRotate: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_E),
    var editorGizmoScale: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_R),
    var editorGizmoSelect: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_Q),
    var editorMeasure: InputBinding = InputBinding(keyboardKey = GLFW.GLFW_KEY_M),
    var editorDeselect: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_ESCAPE
    ),
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
) {

    fun resetToDefaults() {
        // Movement
        moveUp = InputBinding(keyboardKey = GLFW.GLFW_KEY_W, gamepadAxis = 1)
        moveDown = InputBinding(keyboardKey = GLFW.GLFW_KEY_S, gamepadAxis = 1)
        moveLeft = InputBinding(keyboardKey = GLFW.GLFW_KEY_A, gamepadAxis = 0)
        moveRight = InputBinding(keyboardKey = GLFW.GLFW_KEY_D, gamepadAxis = 0)
        sprint = InputBinding(keyboardKey = GLFW.GLFW_KEY_LEFT_SHIFT, gamepadAxis = 4)
        crouch = InputBinding(keyboardKey = GLFW.GLFW_KEY_LEFT_CONTROL, gamepadButton = 4)

        // Jump
        jump = InputBinding(keyboardKey = GLFW.GLFW_KEY_SPACE, gamepadButton = 0)

        // Tricks
        flipLeft = InputBinding(keyboardKey = GLFW.GLFW_KEY_Q, gamepadButton = 4)
        flipRight = InputBinding(keyboardKey = GLFW.GLFW_KEY_E, gamepadButton = 5)
        kickflip = InputBinding(keyboardKey = GLFW.GLFW_KEY_W, gamepadButton = 2)
        heelflip = InputBinding(keyboardKey = GLFW.GLFW_KEY_S, gamepadButton = 3)
        grab = InputBinding(keyboardKey = GLFW.GLFW_KEY_SPACE, gamepadButton = 0)
        manual = InputBinding(keyboardKey = GLFW.GLFW_KEY_LEFT_ALT, gamepadButton = 6)

        // Camera
        cameraLookX = InputBinding(gamepadAxis = 2)
        cameraLookY = InputBinding(gamepadAxis = 3)
        cameraReset = InputBinding(keyboardKey = GLFW.GLFW_KEY_R, gamepadButton = 9)

        // Game State
        pause = InputBinding(keyboardKey = GLFW.GLFW_KEY_ESCAPE, gamepadButton = 7)
        reset = InputBinding(keyboardKey = GLFW.GLFW_KEY_DELETE, gamepadButton = 6)
        stanceChange = InputBinding(keyboardKey = GLFW.GLFW_KEY_LEFT, gamepadButton = 13)
        stanceChangeRight = InputBinding(keyboardKey = GLFW.GLFW_KEY_RIGHT, gamepadButton = 11)

        // Editor
        editorGizmoTranslate = InputBinding(keyboardKey = GLFW.GLFW_KEY_W)
        editorGizmoRotate = InputBinding(keyboardKey = GLFW.GLFW_KEY_E)
        editorGizmoScale = InputBinding(keyboardKey = GLFW.GLFW_KEY_R)
        editorGizmoSelect = InputBinding(keyboardKey = GLFW.GLFW_KEY_Q)
        editorMeasure = InputBinding(keyboardKey = GLFW.GLFW_KEY_M)
        editorDeselect = InputBinding(keyboardKey = GLFW.GLFW_KEY_ESCAPE)

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