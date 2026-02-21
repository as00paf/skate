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
 * ## Properties
 *
 * - [moveDirection]: Normalized 2D movement direction from left stick or WASD
 * - [jumpPressed]: True for one frame when jump button is pressed
 * - [jumpHeld]: True while jump button is held down
 * - [sprintPressed]: True when sprint modifier is active
 * - [cameraLook]: Right stick or mouse delta for camera control
 * - [isGrounded]: True when the entity is touching the ground (synced from physics)
 *
 * ## Usage
 *
 * ```kotlin
 * // In PlayerController or similar gameplay component
 * val inputState = gameObject.getComponent<InputStateComponent>() ?: return
 *
 * if (inputState.jumpPressed) {
 *     applyJump()
 * }
 *
 * val moveDir = inputState.moveDirection
 * if (moveDir.lengthSquared() > 0f) {
 *     applyMovement(moveDir)
 * }
 * ```
 */
@Serializable
class InputStateComponent : Component() {

    /**
     * Normalized 2D movement direction from left stick or WASD keys.
     * Range: [-1, 1] for each axis. Zero vector when no input.
     */
    @Contextual
    var moveDirection = Vector2f(0f, 0f)

    /**
     * True for exactly one frame when the jump button is initially pressed.
     * Use this for actions that should trigger once per button press (e.g., jumping).
     */
    var jumpPressed = false

    /**
     * True while the jump button is held down.
     * Use this for actions that should continue while button is held (e.g., variable jump height).
     */
    var jumpHeld = false

    /**
     * True when the sprint modifier is active (e.g., left trigger, shift key).
     * Use this to modify movement speed or other sprint-related behavior.
     */
    var sprintPressed = false

    /**
     * Right stick or mouse delta for camera control.
     * Range: [-1, 1] for gamepad, unbounded for mouse.
     * X axis = horizontal look (yaw), Y axis = vertical look (pitch).
     */
    @Contextual
    var cameraLook = Vector2f(0f, 0f)

    /**
     * True when the entity is touching the ground.
     * This is synced from the physics system, not directly from input.
     * Use this to determine if jumping or ground-based actions are allowed.
     */
    var isGrounded = false

    /**
     * Resets all input state to default values.
     * Called by [InputSystem] at the start of each frame before polling new inputs.
     */
    fun reset() {
        moveDirection.set(0f, 0f)
        jumpPressed = false
        // jumpHeld is set based on current button state, not reset
        sprintPressed = false
        cameraLook.set(0f, 0f)
        // isGrounded is synced from physics, not reset here
    }

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        reset()
    }
}
