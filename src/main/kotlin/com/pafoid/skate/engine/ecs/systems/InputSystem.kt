package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.EditorCamera
import com.pafoid.skate.editor.data.InputSettings
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.EditorInputStateComponent
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.InputBinding
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.listeners.MouseListener
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

/**
 * System responsible for polling raw hardware inputs and converting them to gameplay state.
 *
 * This system runs at [ExecutionPriority.EARLY] to ensure input state is ready before
 * gameplay systems like [PlayerController] read from [InputStateComponent].
 *
 * ## Responsibilities
 *
 * - Poll raw inputs from [IInputProvider] (keyboard, gamepad) and [MouseListener]
 * - Apply configurable deadzone handling for analog sticks
 * - Implement jump state machine (pressed → held → released)
 * - Write gameplay state to [InputStateComponent] on player entities
 * - Support fully rebindable controls via [InputMappings]
 * - Apply configurable thresholds and sensitivities from [InputSettings]
 *
 * ## Input Mapping
 *
 * All input bindings are configurable via [SettingsManager.settings.inputMappings].
 * Default bindings:
 *
 * | Gameplay Action | Gamepad | Keyboard |
 * |----------------|---------|----------|
 * | Move | Left Stick | W, A, S, D |
 * | Jump | A Button | Space |
 * | Sprint | Left Trigger | Left Shift |
 * | Crouch | LB | Left Control |
 * | Camera Look | Right Stick | Mouse Delta |
 * | Flip Left | LB | Q |
 * | Flip Right | RB | E |
 * | Kickflip | X Button | W |
 * | Heelflip | Y Button | S |
 * | Grab | A Button (air) | Space (air) |
 * | Manual | Back Button | Left Alt |
 * | Pause | Start | Escape |
 * | Reset | Back | Delete |
 * | Stance Change | D-Pad Left/Right | Left/Right Arrow |
 *
 * ## Configuration
 *
 * All deadzones, thresholds, and sensitivities are configurable via
 * [SettingsManager.settings.inputSettings].
 *
 * @param inputProvider Provider for raw hardware inputs
 * @param mouseListener Mouse listener for camera control
 * @param settingsManager Settings manager for input mappings and configuration
 */
class InputSystem(
    private val inputProvider: IInputProvider,
    private val mouseListener: MouseListener,
    private val settingsManager: SettingsManager
) : System(priority = ExecutionPriority.EARLY) {

    // Input mappings (from settings)
    private val mappings: InputMappings
        get() = settingsManager.settings.inputMappings

    // Input settings (from settings)
    private val settings: InputSettings
        get() = settingsManager.settings.inputSettings

    // Jump state tracking
    private var jumpButtonWasPressed = false

    // Keyboard state (reused to reduce allocations)
    private val moveInput = Vector2f()

    override fun init(scene: Scene) {
        super.init(scene)
        jumpButtonWasPressed = false
    }

    override fun update(dt: Float) {
        // Determine if cursor is enabled (editor mode) or disabled (game mode)
        val isCursorEnabled = !inputProvider.isCursorDisabled()

        // Always process editor input when cursor is enabled (even during simulation)
        if (isCursorEnabled) {
            val editorInput = scene.systemManager.getSystem<EditorCamera>()?.editorInput ?: return

            // Reset editor input state for new frame
            editorInput.reset()

            // Poll and process editor inputs (keyboard + mouse)
            pollEditorKeyboardInput(editorInput)
            pollEditorMouseInput(editorInput)
        }

        scene.gameObjectManager.gameObjects.forEach { go ->
            val inputState = go.getComponent<InputStateComponent>() ?: return@forEach

            inputState.reset()
            val inputSettings = settings
            pollGamepadInput(inputState, inputSettings)
            updateJumpState(inputState)
        }
    }

    override fun editorUpdate(dt: Float) {
        val editorInput = scene.systemManager.getSystem<EditorCamera>()?.editorInput ?: return

        editorInput.reset()

        pollEditorKeyboardInput(editorInput)
        pollEditorMouseInput(editorInput)
    }

    override fun start() {
        val editorInputEntity = scene.gameObjectManager.gameObjects.find {
            it.name == "EditorInput" && it.getComponent<EditorInputStateComponent>() != null
        }
        if (editorInputEntity != null) {
            println("[InputSystem] Found EditorInput entity: $editorInputEntity")
        } else {
            println("[InputSystem] WARNING: No EditorInput entity found!")
        }
    }

    /**
     * Polls gamepad input and writes to [inputState].
     * Uses gamepad index 0 (first connected controller).
     * All bindings are read from [InputMappings].
     */
    private fun pollGamepadInput(inputState: InputStateComponent, inputSettings: InputSettings) {
        if (!inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)) return

        val axes = inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1) ?: return
        val buttons = inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1)

        // Movement from left stick (using axis bindings)
        val moveAxis = getAxisFromBinding(mappings.moveUp, mappings.moveDown, axes, inputSettings.leftStickDeadzone)
        val moveStrafe =
            getAxisFromBinding(mappings.moveLeft, mappings.moveRight, axes, inputSettings.leftStickDeadzone)

        if (moveAxis != 0f || moveStrafe != 0f) {
            inputState.moveDirection.set(moveStrafe, moveAxis)
        }

        // Camera look from right stick (using axis bindings)
        val lookX = getAxisFromBinding(mappings.cameraLookX, null, axes, inputSettings.rightStickDeadzone)
        val lookY = getAxisFromBinding(mappings.cameraLookY, null, axes, inputSettings.rightStickDeadzone)

        if (lookX != 0f || lookY != 0f) {
            inputState.cameraLook.set(
                lookX * inputSettings.controllerSensitivity,
                lookY * inputSettings.controllerSensitivity
            )
        }

        // Jump button
        if (buttons != null && mappings.jump.gamepadButton >= 0) {
            val jumpPressed = buttons.getOrNull(mappings.jump.gamepadButton) ?: false
            inputState.jumpHeld = jumpPressed
        }

        // Sprint (trigger axis or button)
        inputState.sprintPressed = checkBindingActive(mappings.sprint, axes, buttons, inputSettings.triggerThreshold)

        // Crouch (button)
        inputState.crouchPressed = checkButtonBindingActive(mappings.crouch, buttons)

        // Trick inputs
        inputState.flipLeftPressed = checkButtonBindingBeginPress(mappings.flipLeft, buttons)
        inputState.flipRightPressed = checkButtonBindingBeginPress(mappings.flipRight, buttons)
        inputState.kickflipPressed = checkButtonBindingBeginPress(mappings.kickflip, buttons)
        inputState.heelflipPressed = checkButtonBindingBeginPress(mappings.heelflip, buttons)
        inputState.grabPressed = checkButtonBindingActive(mappings.grab, buttons)
        inputState.manualPressed = checkButtonBindingActive(mappings.manual, buttons)

        // Camera reset
        inputState.cameraResetPressed = checkButtonBindingBeginPress(mappings.cameraReset, buttons)

        // Game state inputs
        inputState.pausePressed = checkButtonBindingBeginPress(mappings.pause, buttons)
        inputState.resetPressed = checkButtonBindingBeginPress(mappings.reset, buttons)
        inputState.stanceChangePressed = checkButtonBindingBeginPress(mappings.stanceChange, buttons) ||
                checkButtonBindingBeginPress(mappings.stanceChangeRight, buttons)
    }

    /**
     * Polls keyboard input and writes to [inputState].
     * Keyboard takes priority over gamepad for movement.
     * All bindings are read from [InputMappings].
     */
    private fun pollKeyboardInput(inputState: InputStateComponent) {
        moveInput.set(0f, 0f)

        // WASD movement from bindings
        if (inputProvider.isKeyPressed(mappings.moveUp.keyboardKey)) moveInput.y += 1f
        if (inputProvider.isKeyPressed(mappings.moveDown.keyboardKey)) moveInput.y -= 1f
        if (inputProvider.isKeyPressed(mappings.moveLeft.keyboardKey)) moveInput.x -= 1f
        if (inputProvider.isKeyPressed(mappings.moveRight.keyboardKey)) moveInput.x += 1f

        // Normalize if diagonal
        if (moveInput.lengthSquared() > 1f) {
            moveInput.normalize()
        }

        // Keyboard overrides gamepad movement
        if (moveInput.lengthSquared() > 0f) {
            inputState.moveDirection.set(moveInput)
        }

        // Jump
        if (inputProvider.isKeyPressed(mappings.jump.keyboardKey)) {
            inputState.jumpHeld = true
        }

        // Sprint
        if (inputProvider.isKeyPressed(mappings.sprint.keyboardKey)) {
            inputState.sprintPressed = true
        }

        // Crouch
        if (inputProvider.isKeyPressed(mappings.crouch.keyboardKey)) {
            inputState.crouchPressed = true
        }

        // Trick inputs (keyboard begin press detection)
        if (inputProvider.keyBeginPress(mappings.flipLeft.keyboardKey)) inputState.flipLeftPressed = true
        if (inputProvider.keyBeginPress(mappings.flipRight.keyboardKey)) inputState.flipRightPressed = true
        if (inputProvider.keyBeginPress(mappings.kickflip.keyboardKey)) inputState.kickflipPressed = true
        if (inputProvider.keyBeginPress(mappings.heelflip.keyboardKey)) inputState.heelflipPressed = true
        if (inputProvider.isKeyPressed(mappings.grab.keyboardKey)) inputState.grabPressed = true
        if (inputProvider.isKeyPressed(mappings.manual.keyboardKey)) inputState.manualPressed = true

        // Camera reset
        if (inputProvider.keyBeginPress(mappings.cameraReset.keyboardKey)) inputState.cameraResetPressed = true

        // Game state inputs
        if (inputProvider.keyBeginPress(mappings.pause.keyboardKey)) inputState.pausePressed = true
        if (inputProvider.keyBeginPress(mappings.reset.keyboardKey)) inputState.resetPressed = true
        if (inputProvider.keyBeginPress(mappings.stanceChange.keyboardKey) ||
            inputProvider.keyBeginPress(mappings.stanceChangeRight.keyboardKey)
        ) {
            inputState.stanceChangePressed = true
        }
    }

    /**
     * Polls mouse input for camera control.
     * Mouse delta is applied to cameraLook.
     * Mouse sensitivity is configurable from [InputSettings].
     */
    private fun pollMouseInput(inputState: InputStateComponent, inputSettings: InputSettings) {
        // Only apply mouse look when cursor is disabled (gameplay mode)
        if (!inputProvider.isCursorDisabled()) return

        val dx = mouseListener.getDx()
        val dy = mouseListener.getDy()

        // Apply mouse sensitivity
        if (dx != 0f || dy != 0f) {
            inputState.cameraLook.x += dx * inputSettings.mouseSensitivity
            inputState.cameraLook.y += dy * inputSettings.mouseSensitivity
        }
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
     * Gets axis value from a pair of bindings (positive/negative directions).
     * Applies deadzone and returns value in range [-1, 1].
     *
     * For Y-axis (axis 1 = left stick Y, axis 3 = right stick Y), invert the value
     * because GLFW returns negative values when stick is pushed up.
     *
     * For X-axis (axis 0 = left stick X, axis 2 = right stick X): Values are used as-is
     * (negative = left, positive = right).
     *
     * Deadzone handling: Values below the deadzone threshold return 0.
     * Values above deadzone are returned as-is (no rescaling).
     *
     * @param positiveBinding Binding for positive direction
     * @param negativeBinding Binding for negative direction (null if single-axis)
     * @param axes Current gamepad axis values
     * @param deadzone Deadzone threshold
     * @return Axis value in range [-1, 1], or 0 if within deadzone
     */
    private fun getAxisFromBinding(
        positiveBinding: InputBinding,
        negativeBinding: InputBinding?,
        axes: FloatArray,
        deadzone: Float
    ): Float {
        // Determine which axis to read from (try positive binding first, then negative)
        val axisIndex = if (positiveBinding.gamepadAxis >= 0) {
            positiveBinding.gamepadAxis
        } else if (negativeBinding?.gamepadAxis != null && negativeBinding.gamepadAxis >= 0) {
            negativeBinding.gamepadAxis
        } else {
            return 0f
        }

        if (axisIndex >= axes.size) return 0f

        var value = axes[axisIndex]

        // For Y-axis (axis 1 = left stick Y, axis 3 = right stick Y), invert the value
        // because GLFW returns negative values when stick is pushed up
        if (axisIndex == 1 || axisIndex == 3) {
            value = -value
        }

        // Apply deadzone - return 0 if within deadzone
        if (abs(value) < deadzone) return 0f

        // Return value as-is (no rescaling needed)
        return value
    }

    /**
     * Checks if a button binding is currently active.
     *
     * @param binding The input binding to check
     * @param buttons Current gamepad button states
     * @return true if button is pressed, false otherwise
     */
    private fun checkButtonBindingActive(binding: InputBinding, buttons: BooleanArray?): Boolean {
        if (buttons == null || binding.gamepadButton < 0) return false
        return buttons.getOrNull(binding.gamepadButton) ?: false
    }

    /**
     * Checks if a button binding was just pressed (begin press).
     *
     * @param binding The input binding to check
     * @param buttons Current gamepad button states
     * @return true if button was just pressed, false otherwise
     */
    private fun checkButtonBindingBeginPress(binding: InputBinding, buttons: BooleanArray?): Boolean {
        if (buttons == null || binding.gamepadButton < 0) return false
        val current = buttons.getOrNull(binding.gamepadButton) ?: false
        // For begin press, we'd need to track previous state - simplified for now
        return current
    }

    /**
     * Checks if a binding (button or axis) is currently active.
     *
     * @param binding The input binding to check
     * @param axes Current gamepad axis values
     * @param buttons Current gamepad button states
     * @param triggerThreshold Threshold for trigger axes
     * @return true if binding is active, false otherwise
     */
    private fun checkBindingActive(
        binding: InputBinding,
        axes: FloatArray?,
        buttons: BooleanArray?,
        triggerThreshold: Float
    ): Boolean {
        // Check button
        if (binding.gamepadButton >= 0) {
            if (buttons?.getOrNull(binding.gamepadButton) == true) return true
        }

        // Check axis (for triggers)
        if (binding.gamepadAxis >= 0 && axes != null && binding.gamepadAxis < axes.size) {
            var value = axes[binding.gamepadAxis]
            if (binding.inverted) value = -value
            if (value > triggerThreshold) return true
        }

        return false
    }

    // =========================================================================
    // EDITOR INPUT POLLING
    // =========================================================================

    /**
     * Polls editor keyboard input and writes to [editorInput].
     *
     * Editor controls:
     * - WASD: Horizontal camera movement
     * - Space/Shift: Vertical camera movement
     * - Home: Reset camera position
     *
     * @param editorInput Editor input state to write to
     */
    private fun pollEditorKeyboardInput(editorInput: EditorInputStateComponent) {
        val moveInput = Vector2f()

        // WASD movement
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_W)) moveInput.y += 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_S)) moveInput.y -= 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_A)) moveInput.x -= 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_D)) moveInput.x += 1f

        // Normalize if diagonal
        if (moveInput.lengthSquared() > 1f) {
            moveInput.normalize()
        }

        editorInput.moveDirection.set(moveInput)

        // Vertical movement (Space/Shift)
        var verticalInput = 0f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_SPACE)) verticalInput += 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) verticalInput -= 1f

        editorInput.verticalMovement = verticalInput

        // Reset (Home key)
        if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_HOME)) {
            editorInput.resetPressed = true
        }
    }

    /**
     * Polls editor mouse input and writes to [editorInput].
     *
     * Editor mouse controls:
     * - RMB + Move: Camera look rotation
     * - MMB + Move: Orbit rotation
     * - Scroll: Camera zoom
     *
     * @param editorInput Editor input state to write to
     */
    private fun pollEditorMouseInput(editorInput: EditorInputStateComponent) {
        // Check if mouse is inside viewport
        editorInput.isInsideViewport = mouseListener.isInsideViewport()

        // Get mouse delta
        val dx = mouseListener.getDx()
        val dy = mouseListener.getDy()

        // Mouse look (RMB)
        if (mouseListener.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT) && editorInput.isInsideViewport) {
            editorInput.mouseLook.set(dx, dy)
        }
        // Orbit mouse look (MMB) - also capture mouse delta for orbit rotation
        else if (mouseListener.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, true) && editorInput.isInsideViewport) {
            editorInput.mouseLook.set(dx, dy)
        }
        // No mouse button pressed - clear mouse look
        else {
            editorInput.mouseLook.set(0f, 0f)
        }

        // Orbit (MMB)
        val orbitPressed =
            mouseListener.mouseButtonBeginPress(GLFW.GLFW_MOUSE_BUTTON_MIDDLE) && editorInput.isInsideViewport
        val orbitHeld =
            mouseListener.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, true) && editorInput.isInsideViewport

        editorInput.orbitPressed = orbitPressed
        editorInput.orbitHeld = orbitHeld

        // Scroll (zoom)
        if (editorInput.isInsideViewport) {
            editorInput.mouseScroll = mouseListener.getScrollY()
        }
    }
}