package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.input.InputReader

/**
 * Fills an [InputStateComponent] from the [CharacterActions] read through [reader] (INP-10; before, `InputSystem`
 * did this). Edges come from [InputReader.wasActionPressed], so every character sees the same press in a frame.
 */
class CharacterInputSampler(
    private val reader: InputReader,
    var settings: Settings = Settings(),
) {
    data class Settings(
        /** Multiplier on the look actions (stick, -1..1). */
        val stickLookSensitivity: Float = 2.0f,
        /** Multiplier on mouse motion (cursor units) while the cursor is captured. */
        val mouseLookSensitivity: Float = 0.1f,
    )

    fun sample(state: InputStateComponent) {
        state.reset()
        state.moveDirection.set(reader.actionValue(CharacterActions.MOVE_X), reader.actionValue(CharacterActions.MOVE_Y))
        state.cameraLook.set(
            reader.actionValue(CharacterActions.LOOK_X) * settings.stickLookSensitivity,
            reader.actionValue(CharacterActions.LOOK_Y) * settings.stickLookSensitivity,
        )
        if (reader.isCursorCaptured()) {
            state.cameraLook.x += reader.mouseDeltaX() * settings.mouseLookSensitivity
            state.cameraLook.y += reader.mouseDeltaY() * settings.mouseLookSensitivity
        }
        state.jumpHeld = reader.isActionDown(CharacterActions.JUMP)
        state.jumpPressed = reader.wasActionPressed(CharacterActions.JUMP)
        state.sprintPressed = reader.isActionDown(CharacterActions.SPRINT)
        state.crouchPressed = reader.isActionDown(CharacterActions.CROUCH)
        state.mountPressed = reader.wasActionPressed(CharacterActions.TOGGLE_MOUNT)
    }
}
