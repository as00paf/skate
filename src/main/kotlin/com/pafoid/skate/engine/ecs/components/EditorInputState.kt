package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector2f

@Serializable
class EditorInputState {

    @Contextual
    var moveDirection = Vector2f(0f, 0f)
    var verticalMovement = 0f

    @Contextual
    var mouseLook = Vector2f(0f, 0f)
    var mouseScroll = 0f
    var orbitPressed = false
    var orbitHeld = false
    var resetPressed = false
    var isInsideViewport = false
    var isFocused = false

    fun reset() {
        // Movement
        moveDirection.set(0f, 0f)
        verticalMovement = 0f

        // Mouse
        mouseLook.set(0f, 0f)
        mouseScroll = 0f
        orbitPressed = false
        // orbitHeld is set based on current mouse button state, not reset

        // Reset
        resetPressed = false
    }

}
