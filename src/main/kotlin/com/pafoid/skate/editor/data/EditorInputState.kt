package com.pafoid.skate.editor.data

import org.joml.Vector2f

/**
 * Service that stores editor-specific input state.
 */
class EditorInputState {
    var moveDirection = Vector2f(0f, 0f)
    var verticalMovement = 0f
    var mouseLook = Vector2f(0f, 0f)
    var mouseScroll = 0f
    var orbitPressed = false
    var orbitHeld = false
    var resetPressed = false
    var isInsideViewport = false
    var isFocused = true

    fun reset() {
        moveDirection.set(0f, 0f)
        verticalMovement = 0f
        mouseLook.set(0f, 0f)
        mouseScroll = 0f
        orbitPressed = false
        resetPressed = false
    }
}