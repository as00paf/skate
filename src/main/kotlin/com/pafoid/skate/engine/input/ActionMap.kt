package com.pafoid.skate.engine.input

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.sign

/**
 * Group of actions that are active together; only contexts on the reader's [InputContextStack] are read.
 * Open set: the engine ships [GAMEPLAY], [UI] and [EDITOR]; games declare their own (`InputContext("vehicle")`).
 */
@Serializable
@JvmInline
value class InputContext(val id: String) {
    /** Sort order for binding UIs: built-in contexts first in declaration order, then game contexts. */
    val displayOrder: Int get() = BUILT_IN.indexOf(this).let { if (it < 0) BUILT_IN.size else it }

    override fun toString(): String = id

    companion object {
        val GAMEPLAY = InputContext("gameplay")
        val UI = InputContext("ui")
        val EDITOR = InputContext("editor")
        private val BUILT_IN = listOf(GAMEPLAY, UI, EDITOR)
    }
}

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

    /**
     * Physical source without axis direction: what a higher [ContextBlocking.CONSUME_BOUND] context takes away from
     * lower ones (an analog binding reads both directions of its axis).
     */
    fun physicalSourceId(): String = if (this is GamepadAxis) "axis:$axis" else sourceId()

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
    val defaults: List<ActionBinding> = emptyList(),
    /** When true, `InputSystem` publishes an `ActionEvent` on each press and release of this action. */
    val publishEvents: Boolean = false,
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

    /** Profile overrides of actions not defined yet (e.g. game actions registered after the project loaded). */
    private val pending = HashMap<String, List<ActionBinding>>()

    /** Incremented on every change so UIs and caches can detect it. */
    var revision: Int = 0
        private set

    fun define(definition: ActionDefinition): ActionMap {
        definitions[definition.id] = definition
        pending.remove(definition.id)?.let { if (it != definition.defaults) overrides[definition.id] = it }
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
        pending.clear()
        revision++
    }

    /** Customizations, including those kept for actions that are not defined (yet). */
    fun toProfile(name: String = "Default"): BindingProfile =
        BindingProfile(name, overrides = LinkedHashMap<String, List<ActionBinding>>(pending).apply { putAll(overrides) })

    /**
     * Replaces all customizations. Overrides of unknown actions are kept aside and applied if the action is defined
     * later, and saved again by [toProfile], so a profile survives actions registered after it was loaded.
     */
    fun loadProfile(profile: BindingProfile) {
        overrides.clear()
        pending.clear()
        for ((id, list) in profile.overrides) {
            val def = definitions[id]
            if (def == null) pending[id] = list
            else if (list != def.defaults) overrides[id] = list
        }
        revision++
    }

    private fun setBindings(id: String, list: List<ActionBinding>) {
        val defaults = definitions[id]?.defaults
        if (list == defaults) overrides.remove(id) else overrides[id] = list
        revision++
    }
}
