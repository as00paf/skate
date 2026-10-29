package com.pafoid.skate.editor.commands.input

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.BindingProfile
import com.pafoid.skate.engine.input.ConflictResolution
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.RebindResult

/** One user edit of an [ActionMap]. Operations are applied to a scratch copy first, so they can be validated and undone. */
sealed class BindingOperation {
    abstract val label: String
    abstract fun apply(map: ActionMap): RebindResult

    /** Name of the active profile after the operation. */
    open fun profileName(current: String): String = current

    data class Rebind(
        val id: String,
        /** Slot to replace, or null to append a new binding. */
        val index: Int?,
        val binding: ActionBinding,
        val resolution: ConflictResolution = ConflictResolution.REJECT,
    ) : BindingOperation() {
        override val label get() = "Rebind $id"
        override fun apply(map: ActionMap) = map.rebind(id, index, binding, resolution)
    }

    data class Remove(val id: String, val index: Int) : BindingOperation() {
        override val label get() = "Remove binding of $id"
        override fun apply(map: ActionMap) =
            if (map.removeBinding(id, index)) RebindResult.Success else RebindResult.Invalid("No binding slot $index for '$id'")
    }

    data class Clear(val id: String) : BindingOperation() {
        override val label get() = "Clear bindings of $id"
        override fun apply(map: ActionMap): RebindResult {
            if (map.definition(id) == null) return RebindResult.Invalid("Unknown action '$id'")
            map.clearBindings(id)
            return RebindResult.Success
        }
    }

    /** Resets a single action, a category or (with every id) the whole map. */
    data class Reset(val ids: List<String>, override val label: String = "Reset bindings") : BindingOperation() {
        override fun apply(map: ActionMap): RebindResult {
            ids.forEach { map.reset(it) }
            return RebindResult.Success
        }
    }

    data class LoadProfile(val profile: BindingProfile) : BindingOperation() {
        override val label get() = "Load binding profile ${profile.name}"
        override fun apply(map: ActionMap): RebindResult {
            map.loadProfile(profile)
            return RebindResult.Success
        }
        override fun profileName(current: String) = profile.name
    }
}

/** Independent copy of [this] (definitions and customizations). */
fun ActionMap.copy(): ActionMap = ActionMap().also { copy ->
    definitions().forEach { copy.define(it) }
    copy.loadProfile(toProfile())
}

/**
 * Applies a [BindingOperation] to the live action map and mirrors the result into the project's [InputMappings],
 * so it persists. Undo restores the previous profile. Persistence to disk is done by the caller.
 */
class EditBindingsCommand(
    private val map: ActionMap,
    private val mappings: InputMappings,
    val operation: BindingOperation,
) : Command {
    private val before: BindingProfile = map.toProfile(mappings.bindings.name)

    /** Outcome of the operation on a scratch copy; anything but Success means the command must not be executed. */
    val result: RebindResult
    private val after: BindingProfile

    init {
        val scratch = map.copy()
        result = operation.apply(scratch)
        after = scratch.toProfile(operation.profileName(before.name))
    }

    /** True when executing would change nothing (or the operation was rejected). */
    fun isNoOp(): Boolean = result !is RebindResult.Success || after == before

    override fun execute() = restore(after)

    override fun undo() = restore(before)

    private fun restore(profile: BindingProfile) {
        map.loadProfile(profile)
        mappings.bindings = profile
    }

    override fun getDisplayName(): String = operation.label

    override fun getTargetName(): String = "Key Bindings"
}
