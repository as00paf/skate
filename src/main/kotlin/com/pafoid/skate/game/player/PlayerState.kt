package com.pafoid.skate.game.player

sealed class PlayerState {
    object IDLE : PlayerState()
    object WALKING : PlayerState()


    // Will come later with more
    object RIDING : PlayerState()
    object PUSHING : PlayerState()
}