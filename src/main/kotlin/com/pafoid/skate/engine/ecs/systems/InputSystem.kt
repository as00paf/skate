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
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.DefaultActions
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.InputReader
import com.pafoid.skate.engine.input.ProviderRawInput
import org.lwjgl.glfw.GLFW
import kotlin.math.sqrt

class InputSystem(
    val inputProvider: InputProvider,
    private val eventSystem: EventSystem,
) : System(priority = ExecutionPriority.EARLY) {

    /** Action definitions and live bindings. Rebind here, then call `mappings.storeBindings(actionMap)` to persist. */
    val actionMap: ActionMap = DefaultActions.register(ActionMap())

    private val actionReader = ActionInputReader(ProviderRawInput(inputProvider), actionMap) { mappings.deadZones }

    /** Editor-independent key/action read API for scripts and runtime code. */
    val reader: InputReader get() = actionReader

    /** Active contexts (GAMEPLAY and UI by default); only their actions are read. */
    val activeContexts: MutableSet<com.pafoid.skate.engine.input.InputContext> get() = actionReader.activeContexts

    var mappings: InputMappings = InputMappings()
        set(value) {
            field = value
            actionMap.loadProfile(value.bindings)
        }

    private var jumpButtonWasPressed = false
    private var movementWasActive = false

    override fun init(scene: Scene) {
        super.init(scene)
        inputProvider.initializeGamepad()
        jumpButtonWasPressed = false
        movementWasActive = false

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
            pollActions(inputState)
            pollMouseInput(inputState)
            updateJumpState(inputState)
        }

        inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)
    }

    private fun pollActions(inputState: InputStateComponent) {
        val moveX = reader.actionValue(DefaultActions.MOVE_X)
        val moveY = reader.actionValue(DefaultActions.MOVE_Y)

        if (moveX != 0f || moveY != 0f) {
            inputState.moveDirection.set(moveX, moveY)
            val magnitude = sqrt(moveX * moveX + moveY * moveY)
            eventSystem.publish(MovementInput(inputState.moveDirection, magnitude))
            movementWasActive = true
        } else {
            publishNeutralMovementIfNeeded(inputState)
        }

        inputState.cameraLook.set(
            reader.actionValue(DefaultActions.LOOK_X) * CONTROLLER_LOOK_SENSITIVITY,
            reader.actionValue(DefaultActions.LOOK_Y) * CONTROLLER_LOOK_SENSITIVITY
        )

        inputState.jumpHeld = reader.isActionDown(DefaultActions.JUMP)
        inputState.sprintPressed = reader.isActionDown(DefaultActions.SPRINT)
        inputState.crouchPressed = reader.isActionDown(DefaultActions.CROUCH)
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

    private companion object {
        const val CONTROLLER_LOOK_SENSITIVITY = 2.0f
    }
}
