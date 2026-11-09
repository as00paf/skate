package com.pafoid.skate.engine.input

/**
 * The engine's only built-in actions: menu navigation in [InputContext.UI], used by the runtime UI and the pause
 * menu. Gameplay actions are opt-in presets outside this package (e.g. `engine.gameplay.CharacterActions`).
 */
object UiNavigationActions {
    const val CONFIRM = "ui.confirm"
    const val CANCEL = "ui.cancel"
    const val UP = "ui.up"
    const val DOWN = "ui.down"
    const val LEFT = "ui.left"
    const val RIGHT = "ui.right"

    /** Defines the navigation actions that [map] does not have yet; existing definitions are kept. */
    fun register(map: ActionMap): ActionMap {
        val ui = InputContext.UI
        fun def(id: String, vararg defaults: ActionBinding) {
            if (map.definition(id) == null) map.define(ActionDefinition(id, ui, "Navigation", defaults = defaults.toList()))
        }
        def(CONFIRM, ActionBinding.Key(Keys.ENTER), ActionBinding.GamepadButton(0))
        def(CANCEL, ActionBinding.Key(Keys.ESCAPE), ActionBinding.GamepadButton(1))
        def(UP, ActionBinding.Key(Keys.UP), ActionBinding.GamepadButton(10))
        def(RIGHT, ActionBinding.Key(Keys.RIGHT), ActionBinding.GamepadButton(11))
        def(DOWN, ActionBinding.Key(Keys.DOWN), ActionBinding.GamepadButton(12))
        def(LEFT, ActionBinding.Key(Keys.LEFT), ActionBinding.GamepadButton(13))
        return map
    }
}
