package com.pafoid.skate.game.player

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.toWorldMatrix
import com.pafoid.skate.game.skateboard.Stance
import imgui.ImGui
import org.joml.Vector3f
import org.koin.core.component.inject

class PlayerStateManager : Component() {

    private val logger: LoggerService by inject()
    private val stringManager: StringManager by inject()
    private val sceneManager: SceneManager by inject()

    private val playerController: PlayerController? by lazy { gameObject.getComponent<PlayerController>() }

    var currentState: PlayerState = PlayerState.IDLE
        private set

    var currentStance = Stance.REGULAR
    var isSwitch = false
    var isGrounded = false
        private set

    override fun update(dt: Float) {
        when (currentState) {
            is PlayerState.IDLE -> handleIdleState(dt)
            is PlayerState.RIDING -> handleRidingState(dt)
            is PlayerState.PUSHING -> handlePushingState(dt)
            is PlayerState.WALKING -> handleWalkingState(dt)
        }
    }

    private fun handleIdleState(dt: Float) {
        // In IDLE, if we start moving, we are WALKING
        if (playerController?.isMoving() == true) {
            transitionToState(PlayerState.WALKING)
        }
    }

    private fun handleRidingState(dt: Float) {
        playerController?.apply {
            checkIfGrounded()
            updateCurrentStance()
            handleSteering(dt, isGrounded)
            handleJumping(isGrounded)
            handleFlicks(dt)
            handleStability()
            handleCatch(dt, isGrounded)
            updateRidingAnimation(dt)
            updateProceduralLean(dt)
            checkBail(isGrounded)
            if (isPushing()) {
                transitionToState(PlayerState.PUSHING)
            }
        }
    }

    private fun checkIfGrounded() {
        val scene = sceneManager.currentScene ?: return
        val transform = gameObject.getComponent<Transform>() ?: return
        val transformMatrix = transform.toWorldMatrix()

        // Calculate ray start and end in world space
        val rayStart = Vector3f().mulProject(transformMatrix)

        // Ray direction is player-local down
        val localDown = Vector3f(0f, -1f, 0f)
        transformMatrix.transformDirection(localDown)

        // 1.00f is one meter
        val rayEnd = Vector3f(localDown).mul(1.00f).add(rayStart)
        isGrounded = scene.physics3d.raycastClosest(rayStart, rayEnd) != null
    }

    private fun handlePushingState(dt: Float) {
        playerController?.handlePushing(dt, isGrounded)
        // After pushing, we go back to riding
        if (playerController?.isPushing() == false) {
            transitionToState(PlayerState.RIDING)
        }
    }

    private fun handleWalkingState(dt: Float) {
        playerController?.handleWalking(dt)
        playerController?.handleGroundSnapping()

        if (playerController?.isMoving() == false) {
            transitionToState(PlayerState.IDLE)
        }
    }

    fun transitionToState(newState: PlayerState) {
        if (currentState == newState) return

        logger.logEngine("Transitioning from ${currentState::class.simpleName} to ${newState::class.simpleName}", LogLevel.ACTION)
        currentState = newState
    }

    override fun imgui() {
        val currentStateText = if (currentState != null) {
            currentState::class.simpleName ?: "N/A"
        } else "N/A"
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