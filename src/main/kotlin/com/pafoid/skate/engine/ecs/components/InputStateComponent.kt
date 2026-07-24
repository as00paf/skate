package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector2f

@Serializable
class InputStateComponent : Component() {
    @Contextual
    var moveDirection = Vector2f(0f, 0f)
    var sprintPressed = false
    var crouchPressed = false
    var jumpPressed = false
    var jumpHeld = false

    @Contextual
    var cameraLook = Vector2f(0f, 0f)

    var pausePressed = false
    var resetPressed = false

    fun reset() {
        // Movement
        moveDirection.set(0f, 0f)
        sprintPressed = false
        crouchPressed = false

        // Jump (jumpHeld is set based on current button state)
        jumpPressed = false

        // Camera
        cameraLook.set(0f, 0f)

        // Game State
        pausePressed = false
        resetPressed = false
    }

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        reset()
    }
}
