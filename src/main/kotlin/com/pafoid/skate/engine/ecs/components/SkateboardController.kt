package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable

@Serializable
data class SkateboardController(
    var flipLeftPressed: Boolean = false,
    var flipRightPressed: Boolean = false,
    var kickflipPressed: Boolean = false,
    var heelflipPressed: Boolean = false,
    var grabPressed: Boolean = false,
    var manualPressed: Boolean = false,
    var stanceChangePressed: Boolean = false,
) : Component() {

    override fun reset() {
        flipLeftPressed = false
        flipRightPressed = false
        kickflipPressed = false
        heelflipPressed = false
        grabPressed = false
        manualPressed = false
        stanceChangePressed = false
    }
}