package com.pafoid.skate.game.player

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.PhysicsComponent
import com.pafoid.skate.game.skateboard.Stance
import imgui.ImGui
import org.koin.core.component.inject

class PlayerStateManager : Component() {

    private val logger: LoggerService by inject()
    private val stringManager: StringManager by inject()

    private val playerController: PlayerController? by lazy { gameObject.getComponent<PlayerController>() }
    private val physicsComponent: PhysicsComponent? by lazy { gameObject.getComponent<PhysicsComponent>() }

    var currentState: PlayerState = PlayerState.IDLE
        private set
    var isSwitch = false
    var isOnBoard = false
        private set

    var currentStance = Stance.REGULAR

    override fun update(dt: Float) {
        if (isOnBoard) {
            handleOnBoardControls(dt)
        } else {
            handleOffBoardControls(dt)
        }
    }

    private fun handleOnBoardControls(dt: Float) {

    }

    private fun handleOffBoardControls(dt: Float) {
        val controller = playerController ?: return
        val physics = physicsComponent ?: return

        val intent = controller.desiredMoveDirection.length()
        val hasIntent = intent > 0.15f

        // Read speed from PhysicsComponent instead of directly from rigidBody
        val speed = physics.speed

        val newState =
            if (controller.isJumping) {
                PlayerState.JUMPING
            } else if (speed > 0.1f && hasIntent) {
                if (speed > 5f) {
                    PlayerState.RUNNING
                } else {
                    PlayerState.WALKING
                }
            } else {
                PlayerState.IDLE
            }

        transitionToState(newState)
    }

    fun transitionToState(newState: PlayerState) {
        if (currentState == newState) return

        logger.logEngine(
            "Transitioning from ${currentState::class.simpleName} to ${newState::class.simpleName}",
            LogLevel.ACTION
        )
        currentState = newState
    }

    override fun imgui() {
        val currentStateText = currentState::class.simpleName.orEmpty()

        ImGui.text(stringManager.getString("lbl.player.state", currentStateText))
        ImGui.text(stringManager.getString("lbl.player.current_stance", currentStance))
        ImGui.text(stringManager.getString("lbl.player.is_switch", isSwitch))
        if (ImGui.button(stringManager.getString("btn.player.toggle_switch"))) {
            isSwitch = !isSwitch
        }
    }
}