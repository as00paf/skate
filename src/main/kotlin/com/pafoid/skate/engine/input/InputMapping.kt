package com.pafoid.skate.engine.input

import kotlinx.serialization.Serializable
import org.lwjgl.glfw.GLFW

/**
 * Complete input mapping configuration for all gameplay actions.
 *
 * This class contains all input bindings for the game, organized by category:
 * - Movement (direction, sprint, crouch)
 * - Jump (pressed, held)
 * - Tricks (flip, kickflip, heelflip, grab, manual)
 * - Camera (look, reset)
 * - Game State (pause, reset, stance change)
 *
 * All bindings are serializable for saving/loading to configuration files.
 *
 * ## Default Bindings
 *
 * ### Movement
 * - Move Up: W key / Left Stick Up
 * - Move Down: S key / Left Stick Down
 * - Move Left: A key / Left Stick Left
 * - Move Right: D key / Left Stick Right
 * - Sprint: Left Shift / Left Trigger
 * - Crouch: Left Control / Left Bumper
 *
 * ### Jump
 * - Jump: Space / A Button
 *
 * ### Tricks
 * - Flip Left: Q / LB
 * - Flip Right: E / RB
 * - Kickflip: W + Flip / X Button
 * - Heelflip: S + Flip / Y Button
 * - Grab: Space (in air) / A Button (in air)
 * - Manual: Left Alt / Back Button
 *
 * ### Camera
 * - Camera Reset: R / Right Stick Press
 *
 * ### Game State
 * - Pause: Escape / Start Button
 * - Reset: Delete / Back + Start
 * - Stance Change: Left/Right Arrow / D-Pad Left/Right
 */
@Serializable
class InputMappings {

    // =========================================================================
    // MOVEMENT MAPPINGS
    // =========================================================================

    /**
     * Move up (forward) input binding.
     * Default: W key / Left Stick Up (axis)
     * Note: GLFW Y axis is negative when stick is pushed up. The getAxisFromBinding()
     * function inverts Y-axis values to get positive movement for "up".
     */
    var moveUp: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_W,
        gamepadAxis = 1  // AXIS_LEFT_Y
    )

    /**
     * Move down (backward) input binding.
     * Default: S key / Left Stick Down (axis)
     * Note: Uses same axis as moveUp.
     */
    var moveDown: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_S,
        gamepadAxis = 1  // AXIS_LEFT_Y
    )

    /**
     * Move left input binding.
     * Default: A key / Left Stick Left (axis)
     * Note: GLFW X axis is negative when stick is pushed left.
     */
    var moveLeft: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_A,
        gamepadAxis = 0  // AXIS_LEFT_X
    )

    /**
     * Move right input binding.
     * Default: D key / Left Stick Right (axis)
     * Note: Uses same axis as moveLeft.
     */
    var moveRight: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_D,
        gamepadAxis = 0  // AXIS_LEFT_X
    )

    /**
     * Sprint modifier input binding.
     * Default: Left Shift / Left Trigger
     */
    var sprint: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_LEFT_SHIFT,
        gamepadAxis = 4  // AXIS_LEFT_TRIGGER
    )

    /**
     * Crouch modifier input binding.
     * Default: Left Control / Left Bumper
     */
    var crouch: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_LEFT_CONTROL,
        gamepadButton = 4  // BUTTON_LB
    )

    // =========================================================================
    // JUMP MAPPINGS
    // =========================================================================

    /**
     * Jump input binding.
     * Default: Space / A Button
     */
    var jump: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_SPACE,
        gamepadButton = 0  // BUTTON_A
    )

    // =========================================================================
    // TRICK MAPPINGS
    // =========================================================================

    /**
     * Flip left input binding.
     * Used for initiating leftward flip tricks.
     * Default: Q / LB
     */
    var flipLeft: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_Q,
        gamepadButton = 4  // BUTTON_LB
    )

    /**
     * Flip right input binding.
     * Used for initiating rightward flip tricks.
     * Default: E / RB
     */
    var flipRight: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_E,
        gamepadButton = 5  // BUTTON_RB
    )

    /**
     * Kickflip input binding.
     * Used for kickflip-style tricks (board flips forward).
     * Default: W / X Button
     */
    var kickflip: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_W,
        gamepadButton = 2  // BUTTON_X
    )

    /**
     * Heelflip input binding.
     * Used for heelflip-style tricks (board flips backward).
     * Default: S / Y Button
     */
    var heelflip: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_S,
        gamepadButton = 3  // BUTTON_Y
    )

    /**
     * Grab input binding.
     * Used for grab tricks while airborne.
     * Default: Space (same as jump, context-sensitive) / A Button
     */
    var grab: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_SPACE,
        gamepadButton = 0  // BUTTON_A (same as jump, context-sensitive)
    )

    /**
     * Manual input binding.
     * Used for balancing on two wheels (manual/nose manual).
     * Default: Left Alt / Back Button
     */
    var manual: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_LEFT_ALT,
        gamepadButton = 6  // BUTTON_BACK
    )

    // =========================================================================
    // CAMERA MAPPINGS
    // =========================================================================

    /**
     * Camera look horizontal input binding (right stick X axis).
     * Default: Right Stick X axis
     */
    var cameraLookX: InputBinding = InputBinding(
        gamepadAxis = 2  // AXIS_RIGHT_X
    )

    /**
     * Camera look vertical input binding (right stick Y axis).
     * Default: Right Stick Y axis
     * Note: Y-axis is inverted in getAxisFromBinding() for natural camera control.
     */
    var cameraLookY: InputBinding = InputBinding(
        gamepadAxis = 3  // AXIS_RIGHT_Y
    )

    /**
     * Camera reset input binding.
     * Used to reset camera to default position behind player.
     * Default: R / Right Stick Press
     */
    var cameraReset: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_R,
        gamepadButton = 9  // BUTTON_RS
    )

    // =========================================================================
    // GAME STATE MAPPINGS
    // =========================================================================

    /**
     * Pause input binding.
     * Used to toggle pause menu.
     * Default: Escape / Start Button
     */
    var pause: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_ESCAPE,
        gamepadButton = 7  // BUTTON_START
    )

    /**
     * Reset input binding.
     * Used to reset player/level to starting position.
     * Default: Delete / Back + Start (combination)
     */
    var reset: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_DELETE,
        gamepadButton = 6  // BUTTON_BACK (combination handled in InputSystem)
    )

    /**
     * Stance change input binding.
     * Used to toggle between regular/goofy stance or switch/normal.
     * Default: Left/Right Arrow / D-Pad Left/Right
     */
    var stanceChange: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_LEFT,  // Primary: Left arrow
        gamepadButton = 13  // BUTTON_DPAD_LEFT
    )

    /**
     * Alternative stance change binding (for right direction).
     * Used together with [stanceChange] for bidirectional stance switching.
     * Default: Right Arrow / D-Pad Right
     */
    var stanceChangeRight: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_RIGHT,
        gamepadButton = 11  // BUTTON_DPAD_RIGHT
    )

    // =========================================================================
    // EDITOR MAPPINGS (for reference - actual editor bindings in KeyBindings)
    // =========================================================================

    /**
     * Editor: Gizmo translate mode.
     * Default: W (same as move up, context-sensitive in editor)
     */
    var editorGizmoTranslate: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_W
    )

    /**
     * Editor: Gizmo rotate mode.
     * Default: E
     */
    var editorGizmoRotate: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_E
    )

    /**
     * Editor: Gizmo scale mode.
     * Default: R
     */
    var editorGizmoScale: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_R
    )

    /**
     * Editor: Select gizmo.
     * Default: Q
     */
    var editorGizmoSelect: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_Q
    )

    /**
     * Editor: Measure tool.
     * Default: M
     */
    var editorMeasure: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_M
    )

    /**
     * Editor: Deselect all.
     * Default: Escape
     */
    var editorDeselect: InputBinding = InputBinding(
        keyboardKey = GLFW.GLFW_KEY_ESCAPE
    )

    /**
     * Get all bindings as a map for iteration and UI display.
     * @return Map of binding name to InputBinding
     */
    fun getAllBindings(): Map<String, InputBinding> = mapOf(
        // Movement
        "moveUp" to moveUp,
        "moveDown" to moveDown,
        "moveLeft" to moveLeft,
        "moveRight" to moveRight,
        "sprint" to sprint,
        "crouch" to crouch,

        // Jump
        "jump" to jump,

        // Tricks
        "flipLeft" to flipLeft,
        "flipRight" to flipRight,
        "kickflip" to kickflip,
        "heelflip" to heelflip,
        "grab" to grab,
        "manual" to manual,

        // Camera
        "cameraLookX" to cameraLookX,
        "cameraLookY" to cameraLookY,
        "cameraReset" to cameraReset,

        // Game State
        "pause" to pause,
        "reset" to reset,
        "stanceChange" to stanceChange,
        "stanceChangeRight" to stanceChangeRight,

        // Editor
        "editorGizmoTranslate" to editorGizmoTranslate,
        "editorGizmoRotate" to editorGizmoRotate,
        "editorGizmoScale" to editorGizmoScale,
        "editorGizmoSelect" to editorGizmoSelect,
        "editorMeasure" to editorMeasure,
        "editorDeselect" to editorDeselect
    )

    /**
     * Reset all bindings to default values.
     * This creates new InputBinding instances with default values.
     */
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
    }
}
