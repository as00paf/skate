package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionConflict
import com.pafoid.skate.engine.input.ActionDefinition
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.ActionOrigin
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.input.ProjectActionProblem
import com.pafoid.skate.engine.input.ProjectActionSet
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

    /** String keys of mouse buttons 0..4 (left, right, middle, 4, 5). */
    val MOUSE_BUTTON_KEYS = listOf(
        "lbl.keybindings.mouse_left", "lbl.keybindings.mouse_right", "lbl.keybindings.mouse_middle",
        "lbl.keybindings.mouse_button4", "lbl.keybindings.mouse_button5",
    )

    /** Translated binding name shown in the editor. Key caps keep their printed (English) names, as on the keyboard. */
    fun label(binding: ActionBinding, strings: StringManager): String = when (binding) {
        is ActionBinding.Key -> Keys.nameOf(binding.key)
        is ActionBinding.MouseButton -> MOUSE_BUTTON_KEYS.getOrNull(binding.button)?.let(strings::getString)
            ?: strings.getString("lbl.keybindings.mouse_button_n", binding.button + 1)
        is ActionBinding.GamepadButton -> GamepadLayout.buttonName(binding.button, strings)
        is ActionBinding.GamepadAxis -> GamepadLayout.axisName(binding.axis, strings) + if (binding.scale < 0f) " (-)" else ""
    }

    /** Untranslated binding name, for logs and text exports. */
    fun label(binding: ActionBinding): String = when (binding) {
        is ActionBinding.Key -> Keys.nameOf(binding.key)
        is ActionBinding.MouseButton -> "Mouse " + (listOf("Left", "Right", "Middle").getOrNull(binding.button) ?: "Button ${binding.button + 1}")
        is ActionBinding.GamepadButton -> GamepadLayout.buttonName(binding.button)
        is ActionBinding.GamepadAxis -> GamepadLayout.axisName(binding.axis) + if (binding.scale < 0f) " (-)" else ""
    }

    /**
     * Label of [context]: its `lbl.keybindings.context.<id>` translation, else the project context's name (INP-11),
     * else the raw id.
     */
    fun contextLabel(context: InputContext, translate: (String) -> String?, project: ProjectActionSet? = null): String =
        translate("lbl.keybindings.context.${context.id}") ?: project?.context(context.id)?.name?.ifBlank { null } ?: context.id

    /** Shown name of an action: its translation, else a project action's plain name, else its id. */
    fun actionName(definition: ActionDefinition, translate: (String) -> String?): String =
        translate(definition.displayKey) ?: definition.displayName ?: definition.id

    /** String key of the reason [id] cannot be used for a project action (INP-11), or null when it can. */
    fun idErrorKey(id: String, codeIds: Set<String>, takenIds: Set<String>): String? =
        when (ProjectActionSet.idProblem(id, codeIds, takenIds)) {
            null -> null
            ProjectActionProblem.INVALID_ID -> "msg.keybindings.id_invalid"
            ProjectActionProblem.CODE_ID -> "msg.keybindings.id_code"
            ProjectActionProblem.DUPLICATE_ID -> "msg.keybindings.id_taken"
            ProjectActionProblem.UNKNOWN_CONTEXT -> "msg.keybindings.id_invalid"
        }

    /** Contexts an action can use: built in, then those of code actions, then the project's own. */
    fun contextChoices(map: ActionMap, set: ProjectActionSet): List<String> =
        (ProjectActionSet.BUILT_IN_CONTEXTS.sortedBy { InputContext(it).displayOrder } +
            map.definitions().filter { it.origin == ActionOrigin.CODE }.map { it.context.id }.sorted() +
            set.contexts.map { it.id }).distinct()

    /** Existing categories, suggested when creating an action. */
    fun categorySuggestions(map: ActionMap): List<String> =
        map.definitions().map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

    /** First free id of the form `<id>_copy`, `<id>_copy2`, ... for Duplicate. */
    fun duplicateId(id: String, taken: Set<String>): String {
        val base = "${id}_copy"
        if (base !in taken) return base
        return generateSequence(2) { it + 1 }.map { "$base$it" }.first { it !in taken }
    }

    /** [set] with the action [from] renamed to [to] in place (Change id...). */
    fun changeId(set: ProjectActionSet, from: String, to: String): ProjectActionSet =
        set.copy(actions = set.actions.map { if (it.id == from) it.copy(id = to) else it })

    /** Project actions that use the context [id]; a context in use cannot be deleted. */
    fun contextUsers(set: ProjectActionSet, id: String): List<String> = set.actions.filter { it.context == id }.map { it.id }

    /**
     * Actions of *other* contexts bound to the same physical input as [binding] of [actionId] (INP-10 shadowing):
     * while the higher-priority context is active it takes that input. Not a conflict, so rebinding allows it.
     */
    fun sharedWith(map: ActionMap, actionId: String, binding: ActionBinding): List<ActionConflict> {
        val context = map.definition(actionId)?.context ?: return emptyList()
        val source = binding.physicalSourceId()
        return map.definitions().filter { it.context != context }.flatMap { other ->
            map.bindings(other.id).filter { it.physicalSourceId() == source }.map { ActionConflict(other.id, it) }
        }
    }

    /** Groups the actions by context then category, keeping only rows that match [query] (name, id, category or binding). */
    fun groups(
        map: ActionMap,
        query: String,
        nameOf: (ActionDefinition) -> String,
        categoryOf: (ActionDefinition) -> String,
        bindingLabel: (ActionBinding) -> String = ::label,
    ): List<ActionGroup> {
        val q = query.trim().lowercase()
        val rows = map.definitions().map { d ->
            ActionRow(d, nameOf(d), map.bindings(d.id), map.isCustomized(d.id))
        }.filter { row ->
            q.isEmpty() || row.name.lowercase().contains(q) || row.definition.id.lowercase().contains(q) ||
                categoryOf(row.definition).lowercase().contains(q) ||
                row.bindings.any { bindingLabel(it).lowercase().contains(q) }
        }
        return rows.groupBy { it.definition.context to it.definition.category }
            .map { (key, list) ->
                ActionGroup(key.first, key.second, categoryOf(list.first().definition), list)
            }
            .sortedWith(compareBy({ it.context.displayOrder }, { it.context.id }, { it.category }))
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
