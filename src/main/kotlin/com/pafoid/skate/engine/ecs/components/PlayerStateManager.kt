package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.game.player.PlayerState
import com.pafoid.skate.game.skateboard.Stance
import kotlinx.serialization.Serializable

@Serializable
class PlayerStateManager : Component() {

    var currentState: PlayerState = PlayerState.IDLE
        private set
    var isSwitch = false

    var currentStance = Stance.REGULAR

    override fun update(dt: Float) {
        handleOffBoardControls(dt)
    }
    private fun handleOffBoardControls(dt: Float) {
        val controller = gameObject.getComponent<PlayerController>() ?: return
        val physics = gameObject.getComponent<PhysicsComponent>() ?: return

        val intent = controller.desiredMoveDirection.length()
        val hasIntent = intent > 0.15f

        // Read speed from PhysicsComponent instead of directly from rigidBody
        val speed = physics.speed

        val newState =
            if (controller.isJumping) {
                PlayerState.JUMPING
            } else if (!controller.isGrounded) {
                PlayerState.FALLING
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

        currentState = newState
    }

}
