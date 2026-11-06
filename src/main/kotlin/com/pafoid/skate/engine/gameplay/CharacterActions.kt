package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionDefinition
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.Keys

/**
 * Opt-in action preset for a generic character controller (INP-10): walk, look, jump, sprint, crouch and the
 * interact/mount toggle, in [InputContext.GAMEPLAY]. Ids are stable so saved binding profiles keep loading.
 * Read by [CharacterInputSampler].
 */
object CharacterActions {
    const val MOVE_X = "moveX"
    const val MOVE_Y = "moveY"
    const val LOOK_X = "lookX"
    const val LOOK_Y = "lookY"
    const val JUMP = "jump"
    const val SPRINT = "sprint"
    const val CROUCH = "crouch"

    /** Interact / mount or dismount what the character can ride (default: E, gamepad Y). */
    const val TOGGLE_MOUNT = "toggle_mount"

    val ids: List<String> = listOf(MOVE_X, MOVE_Y, LOOK_X, LOOK_Y, JUMP, SPRINT, CROUCH, TOGGLE_MOUNT)

    /** Defines the actions that [map] does not have yet; existing definitions (and their bindings) are kept. */
    fun register(map: ActionMap): ActionMap {
        val g = InputContext.GAMEPLAY
        fun def(id: String, category: String, vararg defaults: ActionBinding) {
            if (map.definition(id) == null) map.define(ActionDefinition(id, g, category, defaults = defaults.toList()))
        }
        def(MOVE_X, "Movement", ActionBinding.Key(Keys.D), ActionBinding.Key(Keys.A, -1f), ActionBinding.GamepadAxis(0))
        def(MOVE_Y, "Movement", ActionBinding.Key(Keys.W), ActionBinding.Key(Keys.S, -1f), ActionBinding.GamepadAxis(1, -1f))
        def(LOOK_X, "Camera", ActionBinding.GamepadAxis(2))
        def(LOOK_Y, "Camera", ActionBinding.GamepadAxis(3, -1f))
        def(JUMP, "Actions", ActionBinding.Key(Keys.SPACE), ActionBinding.GamepadButton(0))
        def(SPRINT, "Actions", ActionBinding.Key(Keys.LEFT_SHIFT), ActionBinding.GamepadAxis(4))
        def(CROUCH, "Actions", ActionBinding.Key(Keys.LEFT_CONTROL), ActionBinding.GamepadButton(4))
        def(TOGGLE_MOUNT, "Actions", ActionBinding.Key(Keys.E), ActionBinding.GamepadButton(3))
        return map
    }
}
