package com.pafoid.skate.engine.player

sealed class PlayerState {
    object IDLE : PlayerState()
    object RIDING : PlayerState()
    object PUSHING : PlayerState()
    object WALKING : PlayerState()
}