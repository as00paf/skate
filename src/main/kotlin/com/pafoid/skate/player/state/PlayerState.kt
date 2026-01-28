package com.pafoid.skate.player.state

sealed class PlayerState {
    object IDLE : PlayerState()
    object RIDING : PlayerState()
    object PUSHING : PlayerState()
    object WALKING : PlayerState()
}