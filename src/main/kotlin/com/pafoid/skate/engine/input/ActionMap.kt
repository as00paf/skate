package com.pafoid.skate.engine.input

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.sign

/** Group of actions that are active together; only active contexts are read by [InputReader]. */
enum class InputContext { GAMEPLAY, UI, EDITOR }

@Serializable
sealed class ActionBinding {
    /** Multiplier applied to the source value; negative scales build the "negative" half of an analog action. */
    abstract val scale: Float

    @Serializable
    data class Key(val key: Int, override val scale: Float = 1f) : ActionBinding()

    @Serializable
    data class MouseButton(val button: Int, override val scale: Float = 1f) : ActionBinding()

    @Serializable
    data class GamepadButton(val button: Int, override val scale: Float = 1f) : ActionBinding()

    @Serializable
    data class GamepadAxis(val axis: Int, override val scale: Float = 1f) : ActionBinding()

    /** Physical source identity used for conflict detection (ignores magnitude, keeps axis direction). */
    fun sourceId(): String = when (this) {
        is Key -> "key:$key"
        is MouseButton -> "mouse:$button"
        is GamepadButton -> "pad:$button"
        is GamepadAxis -> "axis:$axis:${if (sign(scale) < 0f) "-" else "+"}"
    }

    fun withScale(newScale: Float): ActionBinding = when (this) {
        is Key -> copy(scale = newScale)
        is MouseButton -> copy(scale = newScale)
        is GamepadButton -> copy(scale = newScale)
        is GamepadAxis -> copy(scale = newScale)
    }

    /** Human readable label without any backend dependency; UIs may substitute localized names. */
    fun description(): String = when (this) {
        is Key -> Keys.nameOf(key)
        is MouseButton -> "Mouse ${listOf("Left", "Right", "Middle").getOrElse(button) { "Button ${button + 1}" }}"
        is GamepadButton -> "Gamepad Button $button"
        is GamepadAxis -> {
            val name = listOf("Left Stick X", "Left Stick Y", "Right Stick X", "Right Stick Y", "Left Trigger", "Right Trigger")
                .getOrElse(axis) { "Axis $axis" }
            if (scale < 0f) "$name (-)" else name
        }
    }
}

@Serializable
data class ActionDefinition(
    val id: String,
    val context: InputContext,
    /** Grouping used by binding UIs (e.g. "Movement", "Camera"). */
    val category: String,
    /** Translation key for the action name; falls back to [id] in UIs. */
    val displayKey: String = "action.$id",
    val defaults: List<ActionBinding> = emptyList()
)

/** User customizations of an [ActionMap]; only actions that differ from the defaults are stored. */
@Serializable
data class BindingProfile(
    val name: String = "Default",
    val version: Int = CURRENT_VERSION,
    val overrides: Map<String, List<ActionBinding>> = emptyMap()
) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        const val CURRENT_VERSION = 1
        private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

        fun fromJson(text: String): BindingProfile = json.decodeFromString(text)
    }
}

data class ActionConflict(val actionId: String, val binding: ActionBinding)

enum class ConflictResolution {
    /** Do not change anything if the binding is already used by another action in the same context. */
    REJECT,

    /** Remove the binding from the other action(s) and assign it here. */
    UNBIND_OTHERS,

    /** Give the other action(s) the binding that is being replaced (or nothing when adding a new slot). */
    SWAP
}

sealed class RebindResult {
    data object Success : RebindResult()
    data class Conflict(val conflicts: List<ActionConflict>) : RebindResult()
    data class Invalid(val reason: String) : RebindResult()
}

/**
 * Action definitions plus the user's bindings. Conflicts are only detected between actions of the same context.
 * Not thread safe; mutate from the main thread.
 */
class ActionMap {
    private val definitions = LinkedHashMap<String, ActionDefinition>()
    private val overrides = HashMap<String, List<ActionBinding>>()

    /** Incremented on every change so UIs and caches can detect it. */
    var revision: Int = 0
        private set

    fun define(definition: ActionDefinition): ActionMap {
        definitions[definition.id] = definition
        revision++
        return this
    }

    fun definitions(): List<ActionDefinition> = definitions.values.toList()
    fun definitions(context: InputContext): List<ActionDefinition> = definitions.values.filter { it.context == context }
    fun definition(id: String): ActionDefinition? = definitions[id]
    val names: Set<String> get() = definitions.keys

    fun bindings(id: String): List<ActionBinding> = overrides[id] ?: definitions[id]?.defaults ?: emptyList()
    fun isCustomized(id: String): Boolean = id in overrides

    fun findConflicts(id: String, binding: ActionBinding): List<ActionConflict> {
        val context = definitions[id]?.context ?: return emptyList()
        val source = binding.sourceId()
        return definitions.values
            .filter { it.id != id && it.context == context }
            .flatMap { other -> bindings(other.id).filter { it.sourceId() == source }.map { ActionConflict(other.id, it) } }
    }

    /**
     * Replaces the binding at [index] of [id], or appends when [index] is null.
     * With [ConflictResolution.REJECT] nothing changes if the source is already used in the same context.
     */
    fun rebind(
        id: String,
        index: Int?,
        binding: ActionBinding,
        resolution: ConflictResolution = ConflictResolution.REJECT
    ): RebindResult {
        if (id !in definitions) return RebindResult.Invalid("Unknown action '$id'")
        val current = bindings(id)
        if (index != null && index !in current.indices) return RebindResult.Invalid("No binding slot $index for '$id'")

        val conflicts = findConflicts(id, binding)
        if (conflicts.isNotEmpty() && resolution == ConflictResolution.REJECT) return RebindResult.Conflict(conflicts)

        val replaced = index?.let { current[it] }
        val sourceId = binding.sourceId()
        for (other in conflicts.map { it.actionId }.distinct()) {
            val remaining = bindings(other).filter { it.sourceId() != sourceId }.toMutableList()
            if (resolution == ConflictResolution.SWAP && replaced != null) remaining.add(replaced)
            setBindings(other, remaining)
        }

        val updated = current.toMutableList()
        if (index == null) updated.add(binding) else updated[index] = binding
        setBindings(id, updated.distinctBy { it.sourceId() })
        return RebindResult.Success
    }

    fun addBinding(id: String, binding: ActionBinding, resolution: ConflictResolution = ConflictResolution.REJECT) =
        rebind(id, null, binding, resolution)

    fun removeBinding(id: String, index: Int): Boolean {
        val current = bindings(id)
        if (id !in definitions || index !in current.indices) return false
        setBindings(id, current.filterIndexed { i, _ -> i != index })
        return true
    }

    fun clearBindings(id: String) {
        if (id in definitions) setBindings(id, emptyList())
    }

    fun reset(id: String) {
        if (overrides.remove(id) != null) revision++
    }

    fun resetContext(context: InputContext) = definitions(context).forEach { reset(it.id) }

    fun resetAll() {
        overrides.clear()
        revision++
    }

    fun toProfile(name: String = "Default"): BindingProfile = BindingProfile(name, overrides = LinkedHashMap(overrides))

    /** Replaces all customizations; unknown actions are ignored so old profiles keep loading. */
    fun loadProfile(profile: BindingProfile) {
        overrides.clear()
        for ((id, list) in profile.overrides) {
            val def = definitions[id] ?: continue
            if (list != def.defaults) overrides[id] = list
        }
        revision++
    }

    private fun setBindings(id: String, list: List<ActionBinding>) {
        val defaults = definitions[id]?.defaults
        if (list == defaults) overrides.remove(id) else overrides[id] = list
        revision++
    }
}

object DefaultActions {
    const val JUMP = "jump"
    const val SPRINT = "sprint"
    const val CROUCH = "crouch"
    /** Mount / dismount the character's board or vehicle (default: E, gamepad Y). */
    const val TOGGLE_MOUNT = "toggle_mount"
    const val MOVE_X = "moveX"
    const val MOVE_Y = "moveY"
    const val LOOK_X = "lookX"
    const val LOOK_Y = "lookY"
    const val UI_CONFIRM = "ui.confirm"
    const val UI_CANCEL = "ui.cancel"
    const val UI_UP = "ui.up"
    const val UI_DOWN = "ui.down"
    const val UI_LEFT = "ui.left"
    const val UI_RIGHT = "ui.right"

    fun register(map: ActionMap): ActionMap {
        val g = InputContext.GAMEPLAY
        val ui = InputContext.UI
        map.define(ActionDefinition(MOVE_X, g, "Movement", defaults = listOf(
            ActionBinding.Key(Keys.D), ActionBinding.Key(Keys.A, -1f), ActionBinding.GamepadAxis(0))))
        map.define(ActionDefinition(MOVE_Y, g, "Movement", defaults = listOf(
            ActionBinding.Key(Keys.W), ActionBinding.Key(Keys.S, -1f), ActionBinding.GamepadAxis(1, -1f))))
        map.define(ActionDefinition(LOOK_X, g, "Camera", defaults = listOf(ActionBinding.GamepadAxis(2))))
        map.define(ActionDefinition(LOOK_Y, g, "Camera", defaults = listOf(ActionBinding.GamepadAxis(3, -1f))))
        map.define(ActionDefinition(JUMP, g, "Actions", defaults = listOf(
            ActionBinding.Key(Keys.SPACE), ActionBinding.GamepadButton(0))))
        map.define(ActionDefinition(SPRINT, g, "Actions", defaults = listOf(
            ActionBinding.Key(Keys.LEFT_SHIFT), ActionBinding.GamepadAxis(4))))
        map.define(ActionDefinition(CROUCH, g, "Actions", defaults = listOf(
            ActionBinding.Key(Keys.LEFT_CONTROL), ActionBinding.GamepadButton(4))))
        map.define(ActionDefinition(TOGGLE_MOUNT, g, "Actions", defaults = listOf(
            ActionBinding.Key(Keys.E), ActionBinding.GamepadButton(3))))
        map.define(ActionDefinition(UI_CONFIRM, ui, "Navigation", defaults = listOf(
            ActionBinding.Key(Keys.ENTER), ActionBinding.GamepadButton(0))))
        map.define(ActionDefinition(UI_CANCEL, ui, "Navigation", defaults = listOf(
            ActionBinding.Key(Keys.ESCAPE), ActionBinding.GamepadButton(1))))
        map.define(ActionDefinition(UI_UP, ui, "Navigation", defaults = listOf(
            ActionBinding.Key(Keys.UP), ActionBinding.GamepadButton(10))))
        map.define(ActionDefinition(UI_RIGHT, ui, "Navigation", defaults = listOf(
            ActionBinding.Key(Keys.RIGHT), ActionBinding.GamepadButton(11))))
        map.define(ActionDefinition(UI_DOWN, ui, "Navigation", defaults = listOf(
            ActionBinding.Key(Keys.DOWN), ActionBinding.GamepadButton(12))))
        map.define(ActionDefinition(UI_LEFT, ui, "Navigation", defaults = listOf(
            ActionBinding.Key(Keys.LEFT), ActionBinding.GamepadButton(13))))
        return map
    }
}
