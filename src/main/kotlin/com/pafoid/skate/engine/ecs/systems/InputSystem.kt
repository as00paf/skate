package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.events.JumpPressed
import com.pafoid.skate.engine.events.JumpReleased
import com.pafoid.skate.engine.events.MovementInput
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.input.ActionInputReader
import com.pafoid.skate.engine.input.InputBinding
import com.pafoid.skate.engine.input.InputReader
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.ProviderRawInput
import org.lwjgl.glfw.GLFW
import com.pafoid.skate.engine.input.DeadZoneSettings
import com.pafoid.skate.engine.input.TriggerDeadZone
import com.pafoid.skate.engine.input.listeners.GamepadConstants.AXIS_LEFT_TRIGGER
import com.pafoid.skate.engine.input.listeners.GamepadConstants.AXIS_RIGHT_TRIGGER
import com.pafoid.skate.engine.input.listeners.GamepadConstants.AXIS_RIGHT_X
import com.pafoid.skate.engine.input.listeners.GamepadConstants.AXIS_RIGHT_Y
import org.joml.Vector2f

class InputSystem(
    val inputProvider: InputProvider,
    private val eventSystem: EventSystem,
) : System(priority = ExecutionPriority.EARLY) {

    private val actionReader = ActionInputReader(ProviderRawInput(inputProvider)).also {
        it.actions.applyMappings(InputMappings())
    }

    /** Editor-independent key/action read API for scripts and runtime code. */
    val reader: InputReader get() = actionReader

    var mappings: InputMappings = InputMappings()
        set(value) {
            field = value
            actionReader.actions.applyMappings(value)
        }

    private var jumpButtonWasPressed = false
    private var movementWasActive = false
    private var previousButtons: BooleanArray? = null

    override fun init(scene: Scene) {
        super.init(scene)
        inputProvider.initializeGamepad()
        jumpButtonWasPressed = false
        movementWasActive = false
        previousButtons = null

        eventSystem.subscribe<EngineAction.ApplyMappings> { event -> mappings = event.mappings }
    }

    override fun update(dt: Float) {
        inputProvider.refreshGamepadState()
        actionReader.update()
        if (!scene.isRunning) return

        scene.children.forEach { go ->
            val inputState = go.getComponent<InputStateComponent>() ?: return@forEach

            inputState.reset()
            inputState.jumpHeld = false
            pollGamepadInput(inputState)
            pollMouseInput(inputState)
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

        val zones = mappings.deadZones
        readStick(mappings.moveLeft, mappings.moveRight, mappings.moveUp, mappings.moveDown, axes, zones, stickValue)
        val moveStrafe = stickValue.x
        val moveAxis = stickValue.y

        if (moveAxis != 0f || moveStrafe != 0f) {
            inputState.moveDirection.set(moveStrafe, moveAxis)
            // Publish movement event
            val magnitude = kotlin.math.sqrt(moveAxis * moveAxis + moveStrafe * moveStrafe)
            eventSystem.publish(MovementInput(inputState.moveDirection, magnitude))
            movementWasActive = true
        } else {
            publishNeutralMovementIfNeeded(inputState)
        }

        readStick(mappings.cameraLookX, null, mappings.cameraLookY, null, axes, zones, stickValue)
        val lookX = stickValue.x
        val lookY = stickValue.y

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

        inputState.sprintPressed = checkBindingActive(mappings.sprint, axes, buttons, zones)
        inputState.crouchPressed = checkButtonBindingActive(mappings.crouch, buttons)
    }

    private fun pollMouseInput(inputState: InputStateComponent) {
        if (!inputProvider.isCursorDisabled()) return

        val dx = inputProvider.getMouseDx()
        val dy = inputProvider.getMouseDy()
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

    private val stickValue = Vector2f()
    private val fallbackTrigger = TriggerDeadZone()

    private fun axisIndex(positive: InputBinding?, negative: InputBinding?): Int = when {
        positive != null && positive.gamepadAxis >= 0 -> positive.gamepadAxis
        negative != null && negative.gamepadAxis >= 0 -> negative.gamepadAxis
        else -> -1
    }

    /** Reads a stick from the bound X/Y axes and applies the dead zone of the stick that owns the X axis. */
    private fun readStick(
        xPositive: InputBinding,
        xNegative: InputBinding?,
        yPositive: InputBinding,
        yNegative: InputBinding?,
        axes: FloatArray,
        zones: DeadZoneSettings,
        out: Vector2f
    ) {
        val xi = axisIndex(xPositive, xNegative)
        val yi = axisIndex(yPositive, yNegative)
        val rawX = if (xi in axes.indices) axes[xi] else 0f
        // GLFW reports stick-up (Y axes 1 and 3) as negative
        val rawY = if (yi in axes.indices) (if (yi == 1 || yi == 3) -axes[yi] else axes[yi]) else 0f
        val zone = if (xi == AXIS_RIGHT_X || xi == AXIS_RIGHT_Y) zones.rightStick else zones.leftStick
        zone.apply(rawX, rawY, out)
    }

    private fun triggerZone(axis: Int, zones: DeadZoneSettings): TriggerDeadZone = when (axis) {
        AXIS_LEFT_TRIGGER -> zones.leftTrigger
        AXIS_RIGHT_TRIGGER -> zones.rightTrigger
        else -> fallbackTrigger
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
        zones: DeadZoneSettings
    ): Boolean {
        if (binding.gamepadButton >= 0) {
            if (buttons?.getOrNull(binding.gamepadButton) == true) return true
        }

        if (binding.gamepadAxis >= 0 && axes != null && binding.gamepadAxis < axes.size) {
            val value = axes[binding.gamepadAxis]
            val trigger = triggerZone(binding.gamepadAxis, zones)
            if (trigger.isPressed(value)) return true
        }

        return false
    }
}
