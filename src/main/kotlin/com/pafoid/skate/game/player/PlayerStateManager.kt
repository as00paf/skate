package com.pafoid.skate.game.player

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PlayerStateManager(private val playerController: PlayerController) : KoinComponent {
    private val logger: LoggerService by inject()

    var currentState: PlayerState = PlayerState.IDLE
        private set

    fun update(dt: Float) {
        when (currentState) {
            is PlayerState.IDLE -> handleIdleState(dt)
            is PlayerState.RIDING -> handleRidingState(dt)
            is PlayerState.PUSHING -> handlePushingState(dt)
            is PlayerState.WALKING -> handleWalkingState(dt)
        }
    }

    private fun handleIdleState(dt: Float) {
        // In IDLE, if we start moving, we are RIDING
        if (playerController.isMoving()) {
            transitionToState(PlayerState.RIDING)
        }
    }

    private fun handleRidingState(dt: Float) {
        playerController.updateCurrentStance()
        playerController.handleSteering(dt)
        playerController.handleJumping()
        playerController.handleFlicks(dt)
        playerController.handleStability()
        playerController.handleCatch(dt)
        playerController.updateRidingAnimation(dt)
        playerController.updateProceduralLean(dt)
        playerController.checkBail()
        if (playerController.isPushing()) {
            transitionToState(PlayerState.PUSHING)
        }
    }

    private fun handlePushingState(dt: Float) {
        playerController.handlePushing(dt)
        // After pushing, we go back to riding
        if (!playerController.isPushing()) {
            transitionToState(PlayerState.RIDING)
        }
    }

    private fun handleWalkingState(dt: Float) {
        playerController.handleWalking(dt)
        playerController.handleGroundSnapping()
    }

    fun transitionToState(newState: PlayerState) {
        if (currentState == newState) return

        logger.logEngine("Transitioning from ${currentState::class.simpleName} to ${newState::class.simpleName}", LogLevel.ACTION)
        currentState = newState
    }
}