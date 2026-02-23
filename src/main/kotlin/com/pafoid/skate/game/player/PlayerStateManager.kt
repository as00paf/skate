package com.pafoid.skate.game.player

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.game.skateboard.Stance
import imgui.ImGui
import org.koin.core.component.inject

class PlayerStateManager : Component() {

    private val logger: LoggerService by inject()
    private val stringManager: StringManager by inject()

    private val playerController: PlayerController? by lazy { gameObject.getComponent<PlayerController>() }
    private val rigidBody3D: RigidBody3D? by lazy { gameObject.getComponent<RigidBody3D>() }

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
        val rb = rigidBody3D ?: return

        val intent = controller.desiredMoveDirection.length()
        val hasIntent = intent > 0.15f

        // Calculate horizontal speed magnitude (handles both positive and negative velocities)
        val linearVelocity = rb.linearVelocity
        val speed = kotlin.math.sqrt(linearVelocity.x * linearVelocity.x + linearVelocity.z * linearVelocity.z)

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