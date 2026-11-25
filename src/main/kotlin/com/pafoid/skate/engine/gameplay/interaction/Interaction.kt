package com.pafoid.skate.engine.gameplay.interaction

import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionDefinition
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.listeners.GamepadConstants

/** The `interact` action and the built-in action ids (GPL-24 §5.2). */
object Interaction {
    /** Gameplay action that uses the focused interactable. */
    const val ACTION = "interact"

    /** Action preset id for [ActionPresets][com.pafoid.skate.engine.gameplay.ActionPresets]. */
    const val PRESET = "interaction"

    /** [com.pafoid.skate.engine.ecs.components.InteractableComponent.actionId] that puts a pickup in the inventory. */
    const val PICKUP = "pickup"

    /**
     * Adds [ACTION] to [map] when it is missing (an action preset, INP-10). Defaults: F and gamepad D-pad Up. Not E or
     * gamepad X as in the design note: E toggles mounting (`toggle_mount`) and X is a flip in a skate scheme, all in
     * the gameplay context, so one press would do both. Players can rebind it.
     */
    fun bindDefaults(map: ActionMap) {
        if (map.definition(ACTION) != null) return
        map.define(ActionDefinition(ACTION, InputContext.GAMEPLAY, "Actions", defaults = listOf(
            ActionBinding.Key('F'.code), ActionBinding.GamepadButton(GamepadConstants.BUTTON_DPAD_UP))))
    }
}
