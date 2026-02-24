package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector2f

/**
 * Component that stores gameplay input state.
 *
 * This component acts as a bridge between raw hardware inputs (keyboard, gamepad, mouse)
 * and gameplay logic. The [InputSystem] writes to this component, and gameplay systems
 * like [PlayerController] read from it.
 *
 * ## Input Categories
 *
 * - **Movement**: [moveDirection], [sprintPressed], [crouchPressed]
 * - **Jump**: [jumpPressed] (one-frame), [jumpHeld] (continuous)
 * - **Tricks**: [flipLeftPressed], [flipRightPressed], [kickflipPressed], [heelflipPressed], [grabPressed], [manualPressed]
 * - **Camera**: [cameraLook], [cameraResetPressed]
 * - **Game State**: [pausePressed], [resetPressed], [stanceChangePressed]
 * - **Physics**: [isGrounded] (synced from physics system)
 */
@Serializable
class InputStateComponent : Component() {

    // =========================================================================
    // MOVEMENT INPUTS
    // =========================================================================

    /**
     * Normalized 2D movement direction from left stick or WASD keys.
     * Range: [-1, 1] for each axis. Zero vector when no input.
     */
    @Contextual
    var moveDirection = Vector2f(0f, 0f)

    /**
     * True when the sprint modifier is active (e.g., left trigger, shift key).
     * Use this to modify movement speed or other sprint-related behavior.
     */
    var sprintPressed = false

    /**
     * True when the crouch modifier is active (e.g., left control, crouch button).
     * Use this for crouching, manual setup, or low-speed balance.
     */
    var crouchPressed = false

    // =========================================================================
    // JUMP INPUTS
    // =========================================================================

    /**
     * True for exactly one frame when the jump button is initially pressed.
     * Use this for actions that should trigger once per button press (e.g., jumping, ollie).
     */
    var jumpPressed = false

    /**
     * True while the jump button is held down.
     * Use this for actions that should continue while button is held (e.g., variable jump height).
     */
    var jumpHeld = false

    // =========================================================================
    // TRICK INPUTS
    // =========================================================================

    /**
     * True for one frame when flip left input is pressed.
     * Gamepad: LB button | Keyboard: Q key
     * Used for initiating leftward flip tricks or board rotation.
     */
    var flipLeftPressed = false

    /**
     * True for one frame when flip right input is pressed.
     * Gamepad: RB button | Keyboard: E key
     * Used for initiating rightward flip tricks or board rotation.
     */
    var flipRightPressed = false

    /**
     * True for one frame when kickflip input is pressed.
     * Gamepad: X button | Keyboard: W key (when combined with flip input)
     * Used for kickflip-style tricks (board flips forward).
     */
    var kickflipPressed = false

    /**
     * True for one frame when heelflip input is pressed.
     * Gamepad: Y button | Keyboard: S key (when combined with flip input)
     * Used for heelflip-style tricks (board flips backward).
     */
    var heelflipPressed = false

    /**
     * True while grab input is held.
     * Gamepad: A button (in air) | Keyboard: Space (in air)
     * Used for grab tricks while airborne.
     */
    var grabPressed = false

    /**
     * True while manual input is held.
     * Gamepad: Back button | Keyboard: Left Alt
     * Used for balancing on two wheels (manual/nose manual).
     */
    var manualPressed = false

    // =========================================================================
    // CAMERA INPUTS
    // =========================================================================

    /**
     * Right stick or mouse delta for camera control.
     * Range: [-1, 1] for gamepad, unbounded for mouse.
     * X axis = horizontal look (yaw), Y axis = vertical look (pitch).
     */
    @Contextual
    var cameraLook = Vector2f(0f, 0f)

    /**
     * True for one frame when camera reset input is pressed.
     * Gamepad: Right Stick Press | Keyboard: R key
     * Used to reset camera to default position behind player.
     */
    var cameraResetPressed = false

    // =========================================================================
    // GAME STATE INPUTS
    // =========================================================================

    /**
     * True for one frame when pause input is pressed.
     * Gamepad: Start button | Keyboard: Escape key
     * Used to toggle pause menu.
     */
    var pausePressed = false

    /**
     * True for one frame when reset input is pressed.
     * Gamepad: Back + Start | Keyboard: Delete key
     * Used to reset player/level to starting position.
     */
    var resetPressed = false

    /**
     * True for one frame when stance change input is pressed.
     * Gamepad: D-Pad Left/Right | Keyboard: Left/Right Arrow
     * Used to toggle between regular/goofy stance or switch/normal.
     */
    var stanceChangePressed = false

    // =========================================================================
    // PHYSICS STATE (Synced from physics system, not direct input)
    // =========================================================================

    /**
     * True when the entity is touching the ground.
     * This is synced from the physics system, not directly from input.
     * Use this to determine if jumping or ground-based actions are allowed.
     */
    var isGrounded = false

    /**
     * Resets all input state to default values.
     * Called by [InputSystem] at the start of each frame before polling new inputs.
     *
     * Note: [jumpHeld] and [isGrounded] are NOT reset here - they are set based on
     * current input state and physics state respectively.
     */
    fun reset() {
        // Movement
        moveDirection.set(0f, 0f)
        sprintPressed = false
        crouchPressed = false

        // Jump (jumpHeld is set based on current button state)
        jumpPressed = false

        // Tricks
        flipLeftPressed = false
        flipRightPressed = false
        kickflipPressed = false
        heelflipPressed = false
        grabPressed = false
        manualPressed = false

        // Camera
        cameraLook.set(0f, 0f)
        cameraResetPressed = false

        // Game State
        pausePressed = false
        resetPressed = false
        stanceChangePressed = false

        // isGrounded is synced from physics, not reset here
    }

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        reset()
    }
}
