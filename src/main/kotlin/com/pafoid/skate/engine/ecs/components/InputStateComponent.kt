package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector2f

@Serializable
data class InputStateComponent(
    @Contextual
    var moveDirection: Vector2f = Vector2f(0f, 0f),
    var sprintPressed: Boolean = false,
    var crouchPressed: Boolean = false,
    var jumpPressed: Boolean = false,
    var jumpHeld: Boolean = false,
    @Contextual
    var cameraLook: Vector2f = Vector2f(0f, 0f),
    var pausePressed: Boolean = false,
    var resetPressed: Boolean = false,
) : Component() {

    override fun reset() {
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
