package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.EditorCamera
import com.pafoid.skate.editor.data.InputSettings
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.EditorInputStateComponent
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.input.EditorInputMappings
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.InputBinding
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.listeners.MouseListener
import imgui.ImGui
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

/**
 * System responsible for polling raw hardware inputs and converting them to gameplay state.
 *
 * This system runs at [ExecutionPriority.EARLY] to ensure input state is ready before
 * gameplay systems like [PlayerController] read from [InputStateComponent].
 *
 * @param inputProvider Provider for raw hardware inputs
 * @param mouseListener Mouse listener for camera control
 * @param settingsManager Settings manager for input mappings and configuration
 * @param stringManager String manager for localized UI strings
 */
class InputSystem(
    private val inputProvider: IInputProvider,
    private val mouseListener: MouseListener,
    private val settingsManager: SettingsManager,
    private val stringManager: StringManager
) : System(priority = ExecutionPriority.EARLY) {

    private val mappings: InputMappings
        get() = settingsManager.settings.inputMappings

    private val editorMappings: EditorInputMappings
        get() = settingsManager.settings.editorInputMappings

    private val settings: InputSettings
        get() = settingsManager.settings.inputSettings

    private var jumpButtonWasPressed = false
    private var previousButtons: BooleanArray? = null

    override fun init(scene: Scene) {
        super.init(scene)
        jumpButtonWasPressed = false
        previousButtons = null
    }

    override fun update(dt: Float) {
        val isCursorEnabled = !inputProvider.isCursorDisabled()

        if (isCursorEnabled) {
            val editorInput = scene.systemManager.getSystem<EditorCamera>()?.editorInput ?: return

            editorInput.reset()
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

        if (inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)) {
            previousButtons = inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1)?.clone()
        }
    }

    override fun editorUpdate(dt: Float) {
        val editorInput = scene.systemManager.getSystem<EditorCamera>()?.editorInput ?: return

        editorInput.reset()

        pollEditorKeyboardInput(editorInput)
        pollEditorMouseInput(editorInput)
    }

    private fun pollGamepadInput(inputState: InputStateComponent, inputSettings: InputSettings) {
        if (!inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)) return

        val axes = inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1) ?: return
        val buttons = inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1)

        val moveAxis = getAxisFromBinding(mappings.moveUp, mappings.moveDown, axes, inputSettings.leftStickDeadzone)
        val moveStrafe =
            getAxisFromBinding(mappings.moveLeft, mappings.moveRight, axes, inputSettings.leftStickDeadzone)

        if (moveAxis != 0f || moveStrafe != 0f) {
            inputState.moveDirection.set(moveStrafe, moveAxis)
        }

        val lookX = getAxisFromBinding(mappings.cameraLookX, null, axes, inputSettings.rightStickDeadzone)
        val lookY = getAxisFromBinding(mappings.cameraLookY, null, axes, inputSettings.rightStickDeadzone)

        if (lookX != 0f || lookY != 0f) {
            inputState.cameraLook.set(
                lookX * inputSettings.controllerSensitivity,
                lookY * inputSettings.controllerSensitivity
            )
        }

        if (buttons != null && mappings.jump.gamepadButton >= 0) {
            val jumpPressed = buttons.getOrNull(mappings.jump.gamepadButton) ?: false
            inputState.jumpHeld = jumpPressed
        }

        inputState.sprintPressed = checkBindingActive(mappings.sprint, axes, buttons, inputSettings.triggerThreshold)
        inputState.crouchPressed = checkButtonBindingActive(mappings.crouch, buttons)
        inputState.flipLeftPressed = checkButtonBindingBeginPress(mappings.flipLeft, buttons)
        inputState.flipRightPressed = checkButtonBindingBeginPress(mappings.flipRight, buttons)
        inputState.kickflipPressed = checkButtonBindingBeginPress(mappings.kickflip, buttons)
        inputState.heelflipPressed = checkButtonBindingBeginPress(mappings.heelflip, buttons)
        inputState.grabPressed = checkButtonBindingActive(mappings.grab, buttons)
        inputState.manualPressed = checkButtonBindingActive(mappings.manual, buttons)
        inputState.cameraResetPressed = checkButtonBindingBeginPress(mappings.cameraReset, buttons)
        inputState.pausePressed = checkButtonBindingBeginPress(mappings.pause, buttons)
        inputState.resetPressed = checkButtonBindingBeginPress(mappings.reset, buttons)
        inputState.stanceChangePressed = checkButtonBindingBeginPress(mappings.stanceChange, buttons) ||
                checkButtonBindingBeginPress(mappings.stanceChangeRight, buttons)
    }

    private fun pollMouseInput(inputState: InputStateComponent, inputSettings: InputSettings) {
        if (!inputProvider.isCursorDisabled()) return

        val dx = mouseListener.getDx()
        val dy = mouseListener.getDy()

        if (dx != 0f || dy != 0f) {
            inputState.cameraLook.x += dx * inputSettings.mouseSensitivity
            inputState.cameraLook.y += dy * inputSettings.mouseSensitivity
        }
    }

    private fun updateJumpState(inputState: InputStateComponent) {
        if (inputState.jumpHeld && !jumpButtonWasPressed) {
            inputState.jumpPressed = true
        } else {
            inputState.jumpPressed = false
        }

        jumpButtonWasPressed = inputState.jumpHeld
    }

    private fun getAxisFromBinding(
        positiveBinding: InputBinding,
        negativeBinding: InputBinding?,
        axes: FloatArray,
        deadzone: Float
    ): Float {
        val axisIndex = if (positiveBinding.gamepadAxis >= 0) {
            positiveBinding.gamepadAxis
        } else if (negativeBinding?.gamepadAxis != null && negativeBinding.gamepadAxis >= 0) {
            negativeBinding.gamepadAxis
        } else {
            return 0f
        }

        if (axisIndex >= axes.size) return 0f

        var value = axes[axisIndex]

        // Invert Y-axis (axis 1 = left stick Y, axis 3 = right stick Y)
        // because GLFW returns negative values when stick is pushed up
        if (axisIndex == 1 || axisIndex == 3) {
            value = -value
        }

        if (abs(value) < deadzone) return 0f

        return value
    }

    private fun checkButtonBindingActive(binding: InputBinding, buttons: BooleanArray?): Boolean {
        if (buttons == null || binding.gamepadButton < 0) return false
        return buttons.getOrNull(binding.gamepadButton) ?: false
    }

    private fun checkButtonBindingBeginPress(binding: InputBinding, buttons: BooleanArray?): Boolean {
        if (buttons == null || binding.gamepadButton < 0) return false
        val current = buttons.getOrNull(binding.gamepadButton) ?: false
        val previous = previousButtons?.getOrNull(binding.gamepadButton) ?: false
        return current && !previous
    }

    private fun checkBindingActive(
        binding: InputBinding,
        axes: FloatArray?,
        buttons: BooleanArray?,
        triggerThreshold: Float
    ): Boolean {
        if (binding.gamepadButton >= 0) {
            if (buttons?.getOrNull(binding.gamepadButton) == true) return true
        }

        if (binding.gamepadAxis >= 0 && axes != null && binding.gamepadAxis < axes.size) {
            var value = axes[binding.gamepadAxis]
            if (value > triggerThreshold) return true
        }

        return false
    }

    private fun pollEditorKeyboardInput(editorInput: EditorInputStateComponent) {
        val moveInput = Vector2f()

        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_W)) moveInput.y += 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_S)) moveInput.y -= 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_A)) moveInput.x -= 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_D)) moveInput.x += 1f

        if (moveInput.lengthSquared() > 1f) {
            moveInput.normalize()
        }

        editorInput.moveDirection.set(moveInput)

        var verticalInput = 0f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_SPACE)) verticalInput += 1f
        if (inputProvider.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) verticalInput -= 1f

        editorInput.verticalMovement = verticalInput

        if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_HOME)) {
            editorInput.resetPressed = true
        }

        if (inputProvider.keyBeginPress(editorMappings.gizmoTranslate.keyboardKey)) {
            editorInput.gizmoTranslatePressed = true
        }
        if (inputProvider.keyBeginPress(editorMappings.gizmoRotate.keyboardKey)) {
            editorInput.gizmoRotatePressed = true
        }
        if (inputProvider.keyBeginPress(editorMappings.gizmoScale.keyboardKey)) {
            editorInput.gizmoScalePressed = true
        }
        if (inputProvider.keyBeginPress(editorMappings.gizmoSelect.keyboardKey)) {
            editorInput.gizmoSelectPressed = true
        }
        if (inputProvider.keyBeginPress(editorMappings.measureTool.keyboardKey)) {
            editorInput.measureToolPressed = true
        }
        if (inputProvider.keyBeginPress(editorMappings.deselectAll.keyboardKey)) {
            editorInput.deselectAllPressed = true
        }
    }

    private fun pollEditorMouseInput(editorInput: EditorInputStateComponent) {
        editorInput.isInsideViewport = mouseListener.isInsideViewport()

        val dx = mouseListener.getDx()
        val dy = mouseListener.getDy()

        if (mouseListener.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT) && editorInput.isInsideViewport) {
            editorInput.mouseLook.set(dx, dy)
        } else if (mouseListener.isMouseButtonDown(
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                true
            ) && editorInput.isInsideViewport
        ) {
            editorInput.mouseLook.set(dx, dy)
        } else {
            editorInput.mouseLook.set(0f, 0f)
        }

        editorInput.orbitPressed =
            mouseListener.mouseButtonBeginPress(GLFW.GLFW_MOUSE_BUTTON_MIDDLE) && editorInput.isInsideViewport
        editorInput.orbitHeld =
            mouseListener.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, true) && editorInput.isInsideViewport

        if (editorInput.isInsideViewport) {
            editorInput.mouseScroll = mouseListener.getScrollY()
        }
    }

    /**
     * Renders ImGui interface for debugging and tuning input settings.
     *
     * ## Controls
     *
     * - Deadzone settings (left/right stick)
     * - Trigger threshold
     * - Sensitivity settings (mouse/controller)
     * - Movement thresholds
     * - Current input state debugging
     */
    override fun imgui() {
        val inputSettings = settings

        ImGui.separator()
        ImGui.text(stringManager.getString("lbl.input_system.deadzones"))

        // Left Stick Deadzone
        val leftDeadzoneArr = floatArrayOf(inputSettings.leftStickDeadzone)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.left_stick_deadzone"),
                leftDeadzoneArr,
                0.01f,
                0f,
                0.5f,
                "%.2f"
            )
        ) {
            inputSettings.leftStickDeadzone = leftDeadzoneArr[0].coerceIn(0f, 0.5f)
        }

        // Right Stick Deadzone
        val rightDeadzoneArr = floatArrayOf(inputSettings.rightStickDeadzone)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.right_stick_deadzone"),
                rightDeadzoneArr,
                0.01f,
                0f,
                0.5f,
                "%.2f"
            )
        ) {
            inputSettings.rightStickDeadzone = rightDeadzoneArr[0].coerceIn(0f, 0.5f)
        }

        // Trigger Threshold
        val triggerThresholdArr = floatArrayOf(inputSettings.triggerThreshold)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.trigger_threshold"),
                triggerThresholdArr,
                0.01f,
                0f,
                1f,
                "%.2f"
            )
        ) {
            inputSettings.triggerThreshold = triggerThresholdArr[0].coerceIn(0f, 1f)
        }

        ImGui.separator()
        ImGui.text(stringManager.getString("lbl.input_system.sensitivity"))

        // Mouse Sensitivity
        val mouseSensitivityArr = floatArrayOf(inputSettings.mouseSensitivity)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.mouse_sensitivity"),
                mouseSensitivityArr,
                0.01f,
                0.01f,
                1f,
                "%.2f"
            )
        ) {
            inputSettings.mouseSensitivity = mouseSensitivityArr[0].coerceIn(0.01f, 1f)
        }

        // Controller Sensitivity
        val controllerSensitivityArr = floatArrayOf(inputSettings.controllerSensitivity)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.controller_sensitivity"),
                controllerSensitivityArr,
                0.1f,
                0.1f,
                10f,
                "%.1f"
            )
        ) {
            inputSettings.controllerSensitivity = controllerSensitivityArr[0].coerceIn(0.1f, 10f)
        }

        ImGui.separator()
        ImGui.text(stringManager.getString("lbl.input_system.movement_thresholds"))

        // Movement Threshold
        val movementThresholdArr = floatArrayOf(inputSettings.movementThreshold)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.movement_threshold"),
                movementThresholdArr,
                0.01f,
                0f,
                0.5f,
                "%.2f"
            )
        ) {
            inputSettings.movementThreshold = movementThresholdArr[0].coerceIn(0f, 0.5f)
        }

        // Sprint Threshold
        val sprintThresholdArr = floatArrayOf(inputSettings.sprintThreshold)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.sprint_threshold"),
                sprintThresholdArr,
                0.01f,
                0.5f,
                1f,
                "%.2f"
            )
        ) {
            inputSettings.sprintThreshold = sprintThresholdArr[0].coerceIn(0.5f, 1f)
        }

        ImGui.separator()
        ImGui.text(stringManager.getString("lbl.input_system.physics"))

        // Jump Impulse
        val jumpImpulseArr = floatArrayOf(inputSettings.jumpImpulse)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.jump_impulse"),
                jumpImpulseArr,
                1f,
                100f,
                1000f,
                "%.0f"
            )
        ) {
            inputSettings.jumpImpulse = jumpImpulseArr[0].coerceIn(100f, 1000f)
        }

        // Walk Speed
        val walkSpeedArr = floatArrayOf(inputSettings.walkSpeed)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.walk_speed"),
                walkSpeedArr,
                0.1f,
                1f,
                5f,
                "%.1f"
            )
        ) {
            inputSettings.walkSpeed = walkSpeedArr[0].coerceIn(1f, 5f)
        }

        // Run Speed
        val runSpeedArr = floatArrayOf(inputSettings.runSpeed)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.run_speed"),
                runSpeedArr,
                0.1f,
                5f,
                15f,
                "%.1f"
            )
        ) {
            inputSettings.runSpeed = runSpeedArr[0].coerceIn(5f, 15f)
        }

        // Rotation Speed
        val rotationSpeedArr = floatArrayOf(inputSettings.rotationSpeed)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.rotation_speed"),
                rotationSpeedArr,
                1f,
                1f,
                30f,
                "%.0f"
            )
        ) {
            inputSettings.rotationSpeed = rotationSpeedArr[0].coerceIn(1f, 30f)
        }

        // Take Off Time
        val takeOffTimeArr = floatArrayOf(inputSettings.takeOffTime)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.take_off_time"),
                takeOffTimeArr,
                0.1f,
                0.1f,
                2f,
                "%.1f"
            )
        ) {
            inputSettings.takeOffTime = takeOffTimeArr[0].coerceIn(0.1f, 2f)
        }

        // Input Smoothing
        val inputSmoothingArr = floatArrayOf(inputSettings.inputSmoothing)
        if (ImGui.dragFloat(
                stringManager.getString("lbl.input_system.input_smoothing"),
                inputSmoothingArr,
                1f,
                1f,
                20f,
                "%.0f"
            )
        ) {
            inputSettings.inputSmoothing = inputSmoothingArr[0].coerceIn(1f, 20f)
        }

        ImGui.separator()
        ImGui.text(stringManager.getString("lbl.input_system.debug"))

        // Show current gamepad state if connected
        if (inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)) {
            val axes = inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1)
            val buttons = inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1)

            ImGui.text(stringManager.getString("lbl.input_system.gamepad_connected", "Yes"))

            if (axes != null) {
                ImGui.text(stringManager.getString("lbl.input_system.left_stick", axes[0], axes[1]))
                ImGui.text(stringManager.getString("lbl.input_system.right_stick", axes[2], axes[3]))
                ImGui.text(stringManager.getString("lbl.input_system.left_trigger", axes.getOrNull(4) ?: 0f))
                ImGui.text(stringManager.getString("lbl.input_system.right_trigger", axes.getOrNull(5) ?: 0f))
            }

            if (buttons != null) {
                ImGui.text(
                    stringManager.getString(
                        "lbl.input_system.buttons",
                        buttons.getOrNull(0) ?: false,
                        buttons.getOrNull(1) ?: false,
                        buttons.getOrNull(2) ?: false,
                        buttons.getOrNull(3) ?: false
                    )
                )
            }
        } else {
            ImGui.text(stringManager.getString("lbl.input_system.gamepad_connected", "No"))
        }
    }
}