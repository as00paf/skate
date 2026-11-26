package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector2f

/**
 * Per-frame character input snapshot. Filled by `CharacterInputSampler` (engine/gameplay) from the character
 * actions when the scene runs; read by character behaviors and the follow camera. Nothing here is persisted
 * state: every field is @Transient.
 */
@Serializable
data class InputStateComponent(
    @Transient
    val moveDirection: Vector2f = Vector2f(0f, 0f),
    @Transient
    var sprintPressed: Boolean = false,
    @Transient
    var crouchPressed: Boolean = false,
    /** Edge: jump went down this frame. */
    @Transient
    var jumpPressed: Boolean = false,
    @Transient
    var jumpHeld: Boolean = false,
    /** Edge: the mount toggle action went down this frame. */
    @Transient
    var mountPressed: Boolean = false,
    @Transient
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
