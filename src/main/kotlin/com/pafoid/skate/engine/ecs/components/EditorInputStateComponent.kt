package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector2f

/**
 * Component that stores editor-specific input state.
 *
 * This component is separate from [InputStateComponent] because editor inputs
 * have different requirements than gameplay inputs:
 * - Mouse button states (RMB for look, MMB for orbit)
 * - Mouse scroll for zoom
 * - 6DOF camera movement (WASD + Space/Shift + Home)
 * - Editor-specific actions (gizmo modes, etc.)
 *
 * The [com.pafoid.skate.engine.ecs.systems.InputSystem] writes to this component,
 * and editor systems like [com.pafoid.skate.editor.EditorCamera] read from it.
 *
 * ## Properties
 *
 * - [moveDirection]: Normalized 2D movement direction (WASD)
 * - [verticalMovement]: Vertical movement input (Space/Shift)
 * - [mouseLook]: Mouse delta for camera rotation (RMB)
 * - [mouseScroll]: Scroll wheel input for zoom
 * - [orbitPressed]: True for one frame when orbit input is pressed (MMB)
 * - [orbitHeld]: True while orbit input is held
 * - [resetPressed]: True for one frame when reset input is pressed (Home)
 * - [isInsideViewport]: True when mouse is inside the editor viewport
 */
@Serializable
class EditorInputStateComponent : Component() {

    // =========================================================================
    // MOVEMENT INPUTS
    // =========================================================================

    /**
     * Normalized 2D horizontal movement direction from WASD keys.
     * Range: [-1, 1] for each axis. Zero vector when no input.
     * X axis = strafe left/right, Y axis = forward/backward
     */
    @Contextual
    var moveDirection = Vector2f(0f, 0f)

    /**
     * Vertical movement input from Space/Shift keys.
     * Range: [-1, 1]. Positive = up, Negative = down, Zero = no vertical input.
     */
    var verticalMovement = 0f

    // =========================================================================
    // MOUSE INPUTS
    // =========================================================================

    /**
     * Mouse delta for camera rotation (when RMB is held).
     * Range: Unbounded (pixel delta). Zero vector when no mouse movement.
     * X axis = yaw (horizontal rotation), Y axis = pitch (vertical rotation)
     */
    @Contextual
    var mouseLook = Vector2f(0f, 0f)

    /**
     * Scroll wheel input for camera zoom.
     * Range: Unbounded (scroll delta). Zero when no scroll input.
     */
    var mouseScroll = 0f

    /**
     * True for exactly one frame when orbit input (MMB) is initially pressed.
     * Use this to initiate orbit rotation.
     */
    var orbitPressed = false

    /**
     * True while orbit input (MMB) is held down.
     * Use this to continue orbit rotation.
     */
    var orbitHeld = false

    /**
     * True for exactly one frame when camera reset input is pressed (Home key).
     * Use this to reset camera to default position.
     */
    var resetPressed = false

    /**
     * True when the mouse cursor is inside the editor viewport.
     * Use this to determine if mouse inputs should affect the editor camera.
     */
    var isInsideViewport = false

    /**
     * True when the game viewport window is the currently focused ImGui window.
     * Use this to determine if keyboard inputs (like WASD, Space, Shift) should affect the editor camera.
     */
    var isFocused = false

    // =========================================================================
    // GIZMO TOOL INPUTS
    // =========================================================================

    /**
     * True for one frame when gizmo translate input is pressed.
     * Default: W key (configurable via EditorInputMappings)
     */
    var gizmoTranslatePressed = false

    /**
     * True for one frame when gizmo rotate input is pressed.
     * Default: E key (configurable via EditorInputMappings)
     */
    var gizmoRotatePressed = false

    /**
     * True for one frame when gizmo scale input is pressed.
     * Default: R key (configurable via EditorInputMappings)
     */
    var gizmoScalePressed = false

    /**
     * True for one frame when gizmo select input is pressed.
     * Default: Q key (configurable via EditorInputMappings)
     */
    var gizmoSelectPressed = false

    /**
     * True for one frame when measure tool input is pressed.
     * Default: M key (configurable via EditorInputMappings)
     */
    var measureToolPressed = false

    /**
     * True for one frame when deselect all input is pressed.
     * Default: Escape key (configurable via EditorInputMappings)
     */
    var deselectAllPressed = false

    /**
     * Resets all input state to default values.
     * Called by [com.pafoid.skate.engine.ecs.systems.InputSystem] at the start
     * of each frame before polling new inputs.
     */
    fun reset() {
        // Movement
        moveDirection.set(0f, 0f)
        verticalMovement = 0f

        // Mouse
        mouseLook.set(0f, 0f)
        mouseScroll = 0f
        orbitPressed = false
        // orbitHeld is set based on current mouse button state, not reset

        // Reset
        resetPressed = false

        // Gizmo Tools
        gizmoTranslatePressed = false
        gizmoRotatePressed = false
        gizmoScalePressed = false
        gizmoSelectPressed = false
        measureToolPressed = false
        deselectAllPressed = false

        // Viewport state (not reset - maintained by windows/InputSystem)
        // isInsideViewport and isFocused are updated by external UI state
    }

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        reset()
    }
}
