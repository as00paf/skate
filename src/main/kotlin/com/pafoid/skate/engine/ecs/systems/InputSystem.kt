package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.listeners.GamepadConstants
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW
import kotlin.math.sqrt

/**
 * System responsible for polling raw hardware inputs and converting them to gameplay state.
 *
 * This system runs at [ExecutionPriority.EARLY] to ensure input state is ready before
 * gameplay systems like [PlayerController] read from [com.pafoid.skate.engine.ecs.components.InputStateComponent].
 *
 * ## Responsibilities
 *
 * - Poll raw inputs from [com.pafoid.skate.engine.input.IInputProvider] (keyboard, gamepad)
 * - Apply deadzone handling for analog sticks
 * - Implement jump state machine (pressed → held → released)
 * - Write gameplay state to [com.pafoid.skate.engine.ecs.components.InputStateComponent] on player entities
 *
 * ## Input Mapping
 *
 * | Gameplay Action | Gamepad | Keyboard |
 * |----------------|---------|----------|
 * | Move | Left Stick | W, A, S, D |
 * | Jump | A Button | Space |
 * | Sprint | Left Trigger | Left Shift |
 * | Camera Look | Right Stick | Mouse Delta |
 *
 * @param inputProvider Provider for raw hardware inputs
 */
class InputSystem(
    private val inputProvider: IInputProvider
) : System(priority = ExecutionPriority.EARLY) {

    // Deadzone configuration
    private val leftStickDeadzone = 0.15f
    private val rightStickDeadzone = 0.1f
    private val triggerThreshold = 0.5f

    // Jump state tracking
    private var jumpButtonWasPressed = false

    // Keyboard state
    private val moveInput = Vector2f()
    private val cameraInput = Vector2f()

    override fun init(scene: Scene) {
        super.init(scene)
        jumpButtonWasPressed = false
    }

    override fun update(dt: Float) {
        // Find all entities with InputStateComponent
        scene.gameObjectManager.gameObjects.forEach { go ->
            val inputState = go.getComponent<InputStateComponent>() ?: return@forEach

            // Reset input state for new frame
            inputState.reset()

            // Poll and process inputs
            pollGamepadInput(inputState)
            pollKeyboardInput(inputState)
            pollMouseInput(inputState)

            // Update jump state machine
            updateJumpState(inputState)
        }
    }

    /**
     * Polls gamepad input and writes to [inputState].
     * Uses gamepad index 0 (first connected controller).
     */
    private fun pollGamepadInput(inputState: InputStateComponent) {
        if (!inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)) return

        // Left stick - movement
        val leftStick = getLeftStick()
        if (leftStick.lengthSquared() > 0f) {
            inputState.moveDirection.set(leftStick)
        }

        // Right stick - camera look
        val rightStick = getRightStick()
        if (rightStick.lengthSquared() > 0f) {
            inputState.cameraLook.set(rightStick)
        }

        // A button - jump
        val jumpButton = inputProvider.buttonPressed(GLFW.GLFW_JOYSTICK_1, GamepadConstants.BUTTON_A)
        inputState.jumpHeld = jumpButton

        // Left trigger - sprint
        val triggerAxes = inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1)
        if (triggerAxes != null && triggerAxes.size > GamepadConstants.AXIS_LEFT_TRIGGER) {
            inputState.sprintPressed = triggerAxes[GamepadConstants.AXIS_LEFT_TRIGGER] > triggerThreshold
        }
    }

    /**
     * Polls keyboard input and writes to [inputState].
     * Keyboard takes priority over gamepad for movement.
     */
    private fun pollKeyboardInput(inputState: InputStateComponent) {
        moveInput.set(0f, 0f)

        // WASD movement
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_W)) moveInput.y += 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_S)) moveInput.y -= 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_A)) moveInput.x -= 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_D)) moveInput.x += 1f

        // Normalize if diagonal
        if (moveInput.lengthSquared() > 1f) {
            moveInput.normalize()
        }

        // Keyboard overrides gamepad movement
        if (moveInput.lengthSquared() > 0f) {
            inputState.moveDirection.set(moveInput)
        }

        // Space - jump
        val jumpKey = inputProvider.isKeyPressed(GLFW.GLFW_KEY_SPACE)
        if (jumpKey) {
            inputState.jumpHeld = true
        }

        // Left Shift - sprint
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) {
            inputState.sprintPressed = true
        }
    }

    /**
     * Polls mouse input for camera control.
     * Mouse delta is accumulated into cameraLook.
     */
    private fun pollMouseInput(inputState: InputStateComponent) {
        // Mouse look would require MouseListener integration
        // For now, gamepad right stick handles camera look
        // TODO: Integrate MouseListener for camera control
    }

    /**
     * Updates jump state machine.
     *
     * State transitions:
     * - [jumpHeld] true + [jumpButtonWasPressed] false → [jumpPressed] true (rising edge)
     * - [jumpHeld] true → [jumpButtonWasPressed] true (held)
     * - [jumpHeld] false → [jumpButtonWasPressed] false (released)
     */
    private fun updateJumpState(inputState: InputStateComponent) {
        // Detect rising edge (button just pressed)
        if (inputState.jumpHeld && !jumpButtonWasPressed) {
            inputState.jumpPressed = true
        } else {
            inputState.jumpPressed = false
        }

        // Store state for next frame
        jumpButtonWasPressed = inputState.jumpHeld
    }

    /**
     * Gets normalized left stick input with deadzone applied.
     * @return Normalized vector, or zero vector if within deadzone
     */
    private fun getLeftStick(): Vector2f {
        val axes = inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1) ?: return Vector2f(0f, 0f)

        if (axes.size <= GamepadConstants.AXIS_LEFT_Y) return Vector2f(0f, 0f)

        var x = axes[GamepadConstants.AXIS_LEFT_X]
        var y = -axes[GamepadConstants.AXIS_LEFT_Y] // Invert Y for standard coordinate system

        // Apply deadzone
        if (x * x + y * y < leftStickDeadzone * leftStickDeadzone) {
            return Vector2f(0f, 0f)
        }

        // Normalize and rescale to [0, 1] range
        val magnitude = sqrt(x * x + y * y)
        val scaledMagnitude = (magnitude - leftStickDeadzone) / (1f - leftStickDeadzone)
        val clampedMagnitude = scaledMagnitude.coerceIn(0f, 1f)

        return Vector2f(x, y).normalize().mul(clampedMagnitude)
    }

    /**
     * Gets normalized right stick input with deadzone applied.
     * @return Normalized vector, or zero vector if within deadzone
     */
    private fun getRightStick(): Vector2f {
        val axes = inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1) ?: return Vector2f(0f, 0f)

        if (axes.size <= GamepadConstants.AXIS_RIGHT_Y) return Vector2f(0f, 0f)

        var x = axes[GamepadConstants.AXIS_RIGHT_X]
        var y = -axes[GamepadConstants.AXIS_RIGHT_Y] // Invert Y for standard coordinate system

        // Apply deadzone
        if (x * x + y * y < rightStickDeadzone * rightStickDeadzone) {
            return Vector2f(0f, 0f)
        }

        // Normalize and rescale to [0, 1] range
        val magnitude = sqrt(x * x + y * y)
        val scaledMagnitude = (magnitude - rightStickDeadzone) / (1f - rightStickDeadzone)
        val clampedMagnitude = scaledMagnitude.coerceIn(0f, 1f)

        return Vector2f(x, y).normalize().mul(clampedMagnitude)
    }
}