package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector2f

/**
 * Per-frame character input snapshot. Filled by `CharacterInputSampler` (engine/gameplay) from the character
 * actions when the scene runs; read by character behaviors and the follow camera. Nothing here is persisted
 * state, the fields are only serialized for scene compatibility.
 */
@Serializable
data class InputStateComponent(
    @Contextual
    val moveDirection: Vector2f = Vector2f(0f, 0f),
    var sprintPressed: Boolean = false,
    var crouchPressed: Boolean = false,
    /** Edge: jump went down this frame. */
    var jumpPressed: Boolean = false,
    var jumpHeld: Boolean = false,
    /** Edge: the mount toggle action went down this frame. */
    var mountPressed: Boolean = false,
    @Contextual
    val cameraLook: Vector2f = Vector2f(0f, 0f),
) : Component() {

    override fun reset() {
        moveDirection.set(0f, 0f)
        sprintPressed = false
        crouchPressed = false
        jumpPressed = false
        jumpHeld = false
        mountPressed = false
        cameraLook.set(0f, 0f)
    }

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        reset()
    }
}
