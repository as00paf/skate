package com.pafoid.skate.game.player

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.game.skateboard.Stance
import imgui.ImGui
import org.koin.core.component.inject
import kotlin.math.abs
import kotlin.math.max

class PlayerStateManager : Component() {

    private val logger: LoggerService by inject()
    private val stringManager: StringManager by inject()
    private val sceneManager: SceneManager by inject()

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

        val linearVelocity = rb.linearVelocity
        val speed = max(linearVelocity.x, linearVelocity.z)
        val absSpeed = abs(speed)
        logger.logEngine("absSpeed : $absSpeed")

        val newState =
            if (controller.isJumping) {
                PlayerState.JUMPING
            } else if (absSpeed > 0.1f && hasIntent) {
                if (absSpeed > 5f) {
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
        //ImGui.text(stringManager.getString("lbl.player.preferred_stance", preferredStance))
        ImGui.text(stringManager.getString("lbl.player.current_stance", currentStance))
        ImGui.text(stringManager.getString("lbl.player.is_switch", isSwitch))
        //ImGui.text(stringManager.getString("lbl.player.grounded", physics?.isGrounded ?: false))

        /*val vel = rb?.linearVelocity ?: org.joml.Vector3f()
        ImGui.text(stringManager.getString("lbl.player.velocity", String.format("%.2f, %.2f, %.2f", vel.x, vel.y, vel.z)))*/

        if (ImGui.button(stringManager.getString("btn.player.toggle_switch"))) {
            isSwitch = !isSwitch
        }

        /*if (ImGui.button(stringManager.getString("btn.player.toggle_preferred_stance"))) {
            preferredStance = if (preferredStance == PreferredStance.REGULAR) PreferredStance.GOOFY else PreferredStance.REGULAR
        }*/
    }
}