package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputContextStack
import com.pafoid.skate.engine.input.InputReader

/**
 * Fills an [InputStateComponent] from the [CharacterActions] read through [reader] (INP-10; before, `InputSystem`
 * did this). Edges come from [InputReader.wasActionPressed], so every character sees the same press in a frame.
 *
 * Also gives each character its [ActionQuery] ([actionsFor]): behaviors read their own actions there and hold input
 * contexts on [contexts] through [leases]. With [actionMap], [applyPresets] adds the registered [ActionPresets].
 */
class CharacterInputSampler(
    private val reader: InputReader,
    var settings: Settings = Settings(),
    contexts: InputContextStack? = null,
    private val actionMap: ActionMap? = null,
) {
    /** Contexts held by characters; null without a stack (they are then ignored). */
    val leases: InputContextLeases? = contexts?.let(::InputContextLeases)

    /** [owner]'s view of this frame's actions; its context wishes become leases on the stack. */
    fun actionsFor(owner: Any): ActionQuery = ActionQuery.of(reader, leases?.let { l -> { wanted -> l.hold(owner, wanted) } })

    /** Defines the registered presets' actions that are still missing (no change, no revision bump otherwise). */
    fun applyPresets() {
        actionMap?.let(ActionPresets::applyTo)
    }

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
