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
}