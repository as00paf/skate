package com.pafoid.skate.game.input

import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.game.player.InputTuning
import com.pafoid.skate.game.player.TrickType
import org.joml.Vector2f

/**
 * What the rider asks for this frame (INP-10 §7, GPL-21), read from the [SkateActions] in [CharacterInput.actions]
 * rather than from the character's sprint/crouch/jump. Pushing also follows [move] held forward past
 * [InputTuning.pushStickThreshold], as before.
 */
data class SkateIntent(
    val pushHeld: Boolean = false,
    val manualHeld: Boolean = false,
    /** Edge: ollie went down this frame. */
    val olliePressed: Boolean = false,
    /** Trick asked for this frame by the control scheme (a pop when grounded, an air trick when airborne). */
    val trick: TrickType? = null,
    /** Stick that steers, brakes and balances manuals: the scheme's own (feet, triggers) or the character's move. */
    val move: Vector2f = Vector2f(),
) {
    companion object {
        val NONE = SkateIntent()

        /**
         * Reads the skate actions. Call it every frame, on foot too: the reader tracks press edges from the first
         * query on, so a key already held when the rider steps on is not taken for a new ollie.
         *
         * @param tricks this frame of the rider's [SkateTrickInput] (none: plain input, no trick)
         */
        fun read(input: CharacterInput, tuning: InputTuning, tricks: SkateTrickInput.Frame = SkateTrickInput.Frame()): SkateIntent {
            val actions = input.actions
            val move = tricks.move ?: input.move
            val stick = move.length().let { if (it.isNaN()) 0f else it }
            val stickPush = stick > tuning.moveDeadZone && move.y > tuning.pushStickThreshold
            return SkateIntent(
                pushHeld = actions.isDown(SkateActions.PUSH) || stickPush,
                manualHeld = actions.isDown(SkateActions.MANUAL),
                olliePressed = actions.wasPressed(SkateActions.OLLIE),
                trick = tricks.trick,
                move = Vector2f(move),
            )
        }
    }
}
