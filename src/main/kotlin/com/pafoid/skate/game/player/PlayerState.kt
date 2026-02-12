package com.pafoid.skate.game.player

sealed class PlayerState {
    object IDLE : PlayerState()
    object RIDING : PlayerState()
    object PUSHING : PlayerState()
    object WALKING : PlayerState()
}