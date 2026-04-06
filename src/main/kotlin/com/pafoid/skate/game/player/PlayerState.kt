package com.pafoid.skate.game.player

import kotlinx.serialization.Serializable

@Serializable
sealed class PlayerState {
    @Serializable object IDLE : PlayerState()
    @Serializable object WALKING : PlayerState()
    @Serializable object RUNNING : PlayerState()
    @Serializable object JUMPING : PlayerState()
    @Serializable object FALLING : PlayerState()
    @Serializable object LANDING : PlayerState()
    @Serializable object FALLING_IDLE : PlayerState()
    @Serializable object STRAFE_LEFT : PlayerState()
    @Serializable object STRAFE_RIGHT : PlayerState()
    @Serializable object WALK_STRAFE_LEFT : PlayerState()
    @Serializable object WALK_STRAFE_RIGHT : PlayerState()
    @Serializable object TURN_90_LEFT : PlayerState()
    @Serializable object TURN_90_RIGHT : PlayerState()
    @Serializable object TURN_180_LEFT : PlayerState()
    @Serializable object TURN_180_RIGHT : PlayerState()


    // Will come later with more
    @Serializable object RIDING : PlayerState()
    @Serializable object PUSHING : PlayerState()
}