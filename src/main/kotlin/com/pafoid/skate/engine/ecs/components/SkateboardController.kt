package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable

@Serializable
class SkateboardController : Component() {
    var flipLeftPressed = false
    var flipRightPressed = false
    var kickflipPressed = false
    var heelflipPressed = false
    var grabPressed = false
    var manualPressed = false
    var stanceChangePressed = false

    fun reset() {
        flipLeftPressed = false
        flipRightPressed = false
        kickflipPressed = false
        heelflipPressed = false
        grabPressed = false
        manualPressed = false
        stanceChangePressed = false
    }
}