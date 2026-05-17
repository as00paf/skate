package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.contracts.InputMappingsProvider
import com.pafoid.skate.engine.contracts.IStringManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.config.ExecutionPriority
import com.pafoid.skate.engine.events.JumpPressed
import com.pafoid.skate.engine.events.JumpReleased
import com.pafoid.skate.engine.events.MovementInput
import com.pafoid.skate.engine.events.TrickInput
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.InputBinding
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.game.skateboard.TrickType
import imgui.ImGui
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

class InputSystem(
    private val inputProvider: IInputProvider,
    private val mouseListener: MouseListener,
    private val inputMappingsProvider: InputMappingsProvider,
    private val stringManager: IStringManager,
    private val eventSystem: EventSystem,
) : System(priority = ExecutionPriority.EARLY) {

    private val mappings: InputMappings
        get() = inputMappingsProvider.loadInputMappings() ?: InputMappings()

    private var jumpButtonWasPressed = false
    private var movementWasActive = false
    private var previousButtons: BooleanArray? = null

    override fun init(scene: Scene) {
        super.init(scene)
        inputProvider.initializeGamepad()
        jumpButtonWasPressed = false
        movementWasActive = false
        previousButtons = null
    }

    override fun update(dt: Float) {
        inputProvider.refreshGamepadState()
        if (!scene.isRunning) return

        scene.gameObjects.forEach { go ->
            val inputState = go.getComponent<InputStateComponent>() ?: return@forEach

            inputState.reset()
            pollGamepadInput(inputState)
            updateJumpState(inputState)
        }

        if (inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)) {
            previousButtons = inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1)?.clone()
        }
    }

    private fun pollGamepadInput(inputState: InputStateComponent) {
        if (!inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)) {
            publishNeutralMovementIfNeeded(inputState)
            return
        }

        val axes = inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1) ?: run {
            publishNeutralMovementIfNeeded(inputState)
            return
        }
        val buttons = inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1)

        val moveAxis = getAxisFromBinding(mappings.moveUp, mappings.moveDown, axes, 0.15f)
        val moveStrafe =
            getAxisFromBinding(mappings.moveLeft, mappings.moveRight, axes, 0.15f)

        if (moveAxis != 0f || moveStrafe != 0f) {
            inputState.moveDirection.set(moveStrafe, moveAxis)
            // Publish movement event
            val magnitude = kotlin.math.sqrt(moveAxis * moveAxis + moveStrafe * moveStrafe)
            eventSystem.publish(MovementInput(inputState.moveDirection, magnitude))
            movementWasActive = true
        } else {
            publishNeutralMovementIfNeeded(inputState)
        }

        val lookX = getAxisFromBinding(mappings.cameraLookX, null, axes, 0.1f)
        val lookY = getAxisFromBinding(mappings.cameraLookY, null, axes, 0.1f)

        if (lookX != 0f || lookY != 0f) {
            inputState.cameraLook.set(
                lookX * 2.0f,
                lookY * 2.0f
            )
        }

        if (buttons != null && mappings.jump.gamepadButton >= 0) {
            val jumpPressed = buttons.getOrNull(mappings.jump.gamepadButton) ?: false
            inputState.jumpHeld = jumpPressed

        }

        inputState.sprintPressed = checkBindingActive(mappings.sprint, axes, buttons, 0.5f)
        inputState.crouchPressed = checkButtonBindingActive(mappings.crouch, buttons)

        // Publish trick input events
        val flipLeftPressed = checkButtonBindingBeginPress(mappings.flipLeft, buttons)
        val flipRightPressed = checkButtonBindingBeginPress(mappings.flipRight, buttons)
        val kickflipPressed = checkButtonBindingBeginPress(mappings.kickflip, buttons)
        val heelflipPressed = checkButtonBindingBeginPress(mappings.heelflip, buttons)
        val grabPressed = checkButtonBindingActive(mappings.grab, buttons)
        val manualPressed = checkButtonBindingActive(mappings.manual, buttons)

        if (flipLeftPressed) eventSystem.publish(TrickInput(TrickType.FLIP_LEFT, true))
        if (flipRightPressed) eventSystem.publish(TrickInput(TrickType.FLIP_RIGHT, true))
        if (kickflipPressed) eventSystem.publish(TrickInput(TrickType.KICKFLIP, true))
        if (heelflipPressed) eventSystem.publish(TrickInput(TrickType.HEELFLIP, true))
        if (grabPressed) eventSystem.publish(TrickInput(TrickType.GRAB, true))
        if (manualPressed) eventSystem.publish(TrickInput(TrickType.MANUAL, true))

        inputState.flipLeftPressed = flipLeftPressed
        inputState.flipRightPressed = flipRightPressed
        inputState.kickflipPressed = kickflipPressed
        inputState.heelflipPressed = heelflipPressed
        inputState.grabPressed = grabPressed
        inputState.manualPressed = manualPressed
        inputState.cameraResetPressed = checkButtonBindingBeginPress(mappings.cameraReset, buttons)
        inputState.pausePressed = checkButtonBindingBeginPress(mappings.pause, buttons)
        inputState.resetPressed = checkButtonBindingBeginPress(mappings.reset, buttons)
        inputState.stanceChangePressed = checkButtonBindingBeginPress(mappings.stanceChange, buttons) ||
                checkButtonBindingBeginPress(mappings.stanceChangeRight, buttons)
    }

    private fun pollMouseInput(inputState: InputStateComponent) {
        if (!inputProvider.isCursorDisabled()) return

        val dx = mouseListener.getDx()
        val dy = mouseListener.getDy()

        if (dx != 0f || dy != 0f) {
            inputState.cameraLook.x += dx * 0.1f
            inputState.cameraLook.y += dy * 0.1f
        }
    }

    private fun updateJumpState(inputState: InputStateComponent) {
        if (inputState.jumpHeld && !jumpButtonWasPressed) {
            inputState.jumpPressed = true
            eventSystem.publish(JumpPressed(1.0f))
        } else if (!inputState.jumpHeld && jumpButtonWasPressed) {
            eventSystem.publish(JumpReleased)
        }

        jumpButtonWasPressed = inputState.jumpHeld
    }

    private fun publishNeutralMovementIfNeeded(inputState: InputStateComponent) {
        if (!movementWasActive) return
        inputState.moveDirection.set(0f, 0f)
        eventSystem.publish(MovementInput(inputState.moveDirection, 0f))
        movementWasActive = false
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

    /**
     * Renders ImGui interface for debugging and tuning input settings.
     *
     * ## Controls
     *
     * - Current input state debugging
     */
    override fun imgui() {
        ImGui.separator()
        ImGui.textColored(
            0.5f,
            0.5f,
            0.5f,
            1f,
            stringManager.getString("lbl.input_system.configuration_pending")
        )
        ImGui.separator()
        
        ImGui.text(stringManager.getString("lbl.input_system.debug"))

        // Show current gamepad state if connected
        if (inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)) {
            val axes = inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1)
            val buttons = inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1)

            ImGui.text(
                stringManager.getString(
                    "lbl.input_system.gamepad_connected",
                    stringManager.getString("lbl.input_system.yes")
                )
            )

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
            ImGui.text(
                stringManager.getString(
                    "lbl.input_system.gamepad_connected",
                    stringManager.getString("lbl.input_system.no")
                )
            )
        }
    }
}
