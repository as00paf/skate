package com.pafoid.skate.game.input

import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.game.player.InputTuning

/**
 * What the rider asks for this frame (INP-10 §7), read from the [SkateActions] in [CharacterInput.actions] rather
 * than from the character's sprint/crouch/jump. Pushing also follows the stick held forward past
 * [InputTuning.pushStickThreshold], as before.
 */
data class SkateIntent(
    val pushHeld: Boolean = false,
    val manualHeld: Boolean = false,
    /** Edge: ollie went down this frame. */
    val olliePressed: Boolean = false,
) {
    companion object {
        val NONE = SkateIntent()

        /**
         * Reads the skate actions. Call it every frame, on foot too: the reader tracks press edges from the first
         * query on, so a key already held when the rider steps on is not taken for a new ollie.
         */
        fun read(input: CharacterInput, tuning: InputTuning): SkateIntent {
            val actions = input.actions
            val stick = input.move.length().let { if (it.isNaN()) 0f else it }
            val stickPush = stick > tuning.moveDeadZone && input.move.y > tuning.pushStickThreshold
            return SkateIntent(
                pushHeld = actions.isDown(SkateActions.PUSH) || stickPush,
                manualHeld = actions.isDown(SkateActions.MANUAL),
                olliePressed = actions.wasPressed(SkateActions.OLLIE),
            )
        }
    }
}
