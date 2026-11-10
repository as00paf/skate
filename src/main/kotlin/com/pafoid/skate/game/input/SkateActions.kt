package com.pafoid.skate.game.input

import com.pafoid.skate.engine.input.ActionDefinition
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputContext

/**
 * Skateboarding actions (INP-10 §7, GPL-21), read while riding. They live in their own [CONTEXT], which
 * [SkateInputContext] holds above `gameplay` while the skater is on the board, so the same keys push, manual and
 * ollie on the board and sprint, crouch and jump on foot. Their default bindings come from the selected
 * [ControlScheme] ([SkateControls.scheme]); a scheme leaves the actions it does not use unbound.
 */
object SkateActions {
    val CONTEXT = InputContext("skate")

    const val PUSH = "skate.push"
    const val MANUAL = "skate.manual"
    const val OLLIE = "skate.ollie"
    /** Flip trick button, combined with a direction (THPS). */
    const val FLIP = "skate.flip"
    /** Trick stick (EA Skate flick-it) or back foot (Session, Skater XL): right stick, IJKL. */
    const val TRICK_X = "skate.trickX"
    const val TRICK_Y = "skate.trickY"
    /** Front foot (Session, Skater XL): left stick, WASD. */
    const val FRONT_X = "skate.frontX"
    const val FRONT_Y = "skate.frontY"
    /** Turning with the triggers (Skater XL): -1 left, +1 right. */
    const val STEER = "skate.steer"

    const val CATEGORY = "Skate"

    val ids: List<String> = listOf(PUSH, MANUAL, OLLIE, FLIP, TRICK_X, TRICK_Y, FRONT_X, FRONT_Y, STEER)

    /**
     * Defines the skate actions with [scheme]'s defaults. Actions already defined that way are left alone (no
     * revision change); a scheme switch redefines the defaults. The player's own bindings (profile overrides, by
     * action id) are kept across schemes.
     */
    fun register(map: ActionMap, scheme: ControlScheme = SkateControls.scheme): ActionMap {
        for (id in ids) {
            val wanted = ActionDefinition(id, CONTEXT, CATEGORY, defaults = scheme.bindings[id].orEmpty())
            if (map.definition(id) != wanted) map.define(wanted)
        }
        return map
    }
}
