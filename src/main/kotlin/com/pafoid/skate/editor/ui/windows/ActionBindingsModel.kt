package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionDefinition
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.input.RawInput

data class ActionRow(
    val definition: ActionDefinition,
    val name: String,
    val bindings: List<ActionBinding>,
    val customized: Boolean,
)

data class ActionGroup(val context: InputContext, val category: String, val categoryLabel: String, val rows: List<ActionRow>)

/** Pure view model of the action list: grouping by context and category, plus search. */
object ActionBindingsModel {

    fun label(binding: ActionBinding): String = when (binding) {
        is ActionBinding.Key -> Keys.nameOf(binding.key)
        is ActionBinding.MouseButton -> "Mouse " + (listOf("Left", "Right", "Middle").getOrNull(binding.button) ?: "Button ${binding.button + 1}")
        is ActionBinding.GamepadButton -> GamepadLayout.buttonName(binding.button)
        is ActionBinding.GamepadAxis -> GamepadLayout.axisName(binding.axis) + if (binding.scale < 0f) " (-)" else ""
    }

    /** Groups the actions by context then category, keeping only rows that match [query] (name, id, category or binding). */
    fun groups(
        map: ActionMap,
        query: String,
        nameOf: (ActionDefinition) -> String,
        categoryOf: (ActionDefinition) -> String,
    ): List<ActionGroup> {
        val q = query.trim().lowercase()
        val rows = map.definitions().map { d ->
            ActionRow(d, nameOf(d), map.bindings(d.id), map.isCustomized(d.id))
        }.filter { row ->
            q.isEmpty() || row.name.lowercase().contains(q) || row.definition.id.lowercase().contains(q) ||
                categoryOf(row.definition).lowercase().contains(q) ||
                row.bindings.any { label(it).lowercase().contains(q) }
        }
        return rows.groupBy { it.definition.context to it.definition.category }
            .map { (key, list) ->
                ActionGroup(key.first, key.second, categoryOf(list.first().definition), list)
            }
            .sortedWith(compareBy({ it.context.ordinal }, { it.category }))
    }
}

/**
 * Click-to-rebind capture. Create it when capture starts (it records the idle state of the gamepad axes),
 * then call [poll] every frame until it returns a binding.
 */
class BindingCapture(private val raw: RawInput, val kind: Kind, private val pad: Int = 0) {
    enum class Kind { KEYBOARD, GAMEPAD }

    private val baseline = FloatArray(AXES) { raw.gamepadAxis(it, pad) }

    fun poll(): ActionBinding? = when (kind) {
        Kind.KEYBOARD -> (FIRST_KEY..LAST_KEY).firstOrNull { raw.wasKeyPressed(it) }?.let { ActionBinding.Key(it) }
        Kind.GAMEPAD -> pollGamepad()
    }

    private fun pollGamepad(): ActionBinding? {
        (0 until BUTTONS).firstOrNull { raw.wasGamepadButtonPressed(it, pad) }?.let { return ActionBinding.GamepadButton(it) }
        for (axis in 0 until AXES) {
            val delta = raw.gamepadAxis(axis, pad) - baseline[axis]
            if (kotlin.math.abs(delta) >= AXIS_THRESHOLD) return ActionBinding.GamepadAxis(axis, if (delta < 0f) -1f else 1f)
        }
        return null
    }

    companion object {
        const val FIRST_KEY = 32
        const val LAST_KEY = 348
        const val BUTTONS = 15
        const val AXES = 6
        const val AXIS_THRESHOLD = 0.7f
    }
}
