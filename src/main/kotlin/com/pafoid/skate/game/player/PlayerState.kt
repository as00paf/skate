package com.pafoid.skate.game.player

sealed class PlayerState {
    object IDLE : PlayerState()
    object WALKING : PlayerState()
    object RUNNING : PlayerState()
    object JUMPING : PlayerState()
    object FALLING : PlayerState()
    object LANDING : PlayerState()
    object FALLING_IDLE : PlayerState()
    object STRAFE_LEFT : PlayerState()
    object STRAFE_RIGHT : PlayerState()
    object WALK_STRAFE_LEFT : PlayerState()
    object WALK_STRAFE_RIGHT : PlayerState()
    object TURN_90_LEFT : PlayerState()
    object TURN_90_RIGHT : PlayerState()
    object TURN_180_LEFT : PlayerState()
    object TURN_180_RIGHT : PlayerState()


    // Will come later with more
    object RIDING : PlayerState()
    object PUSHING : PlayerState()
}