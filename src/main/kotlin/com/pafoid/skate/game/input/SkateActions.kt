package com.pafoid.skate.game.input

import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionDefinition
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.input.listeners.GamepadConstants

/**
 * Skateboarding actions (INP-10 §7), read while riding. They live in their own [CONTEXT], which [SkateInputContext]
 * holds above `gameplay` while the skater is on the board, so the same keys push, manual and ollie on the board and
 * sprint, crouch and jump on foot. The defaults are the keys that did this before INP-10 (via sprint/crouch/jump).
 */
object SkateActions {
    val CONTEXT = InputContext("skate")

    const val PUSH = "skate.push"
    const val MANUAL = "skate.manual"
    const val OLLIE = "skate.ollie"

    const val CATEGORY = "Skate"

    val ids: List<String> = listOf(PUSH, MANUAL, OLLIE)

    /** Defines the skate actions that [map] lacks; existing definitions and the player's bindings are kept. */
    fun register(map: ActionMap): ActionMap {
        fun def(id: String, vararg defaults: ActionBinding) {
            if (map.definition(id) == null) map.define(ActionDefinition(id, CONTEXT, CATEGORY, defaults = defaults.toList()))
        }
        def(PUSH, ActionBinding.Key(Keys.LEFT_SHIFT), ActionBinding.GamepadAxis(GamepadConstants.AXIS_LEFT_TRIGGER))
        def(MANUAL, ActionBinding.Key(Keys.LEFT_CONTROL), ActionBinding.GamepadButton(GamepadConstants.BUTTON_LB))
        def(OLLIE, ActionBinding.Key(Keys.SPACE), ActionBinding.GamepadButton(GamepadConstants.BUTTON_A))
        return map
    }
}
