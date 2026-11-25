package com.pafoid.skate.editor.commands.input

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.KeyedCommand
import com.pafoid.skate.editor.commands.ReportsFailure
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.ActionOrigin
import com.pafoid.skate.engine.input.BindingProfile
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.ProjectActionSet
import com.pafoid.skate.engine.input.ProjectActions
import com.pafoid.skate.engine.input.RebindResult

/**
 * Snapshot edit of the project's actions asset (INP-11). Execute and undo write the asset (through [write], an
 * atomic file write in the editor) and apply it to the live [ActionMap]; a failed write changes nothing and is
 * reported. The first edit of a project without an asset gives it [ProjectActionSet.DEFAULT_PATH].
 * [afterProfile] carries the code-action overrides of a binding edit that also touched code actions (a swap).
 */
class EditProjectActionsCommand(
    private val map: ActionMap,
    private val mappings: InputMappings,
    private val actions: ProjectActions,
    private val write: (path: String, text: String) -> Unit,
    private val after: ProjectActionSet,
    override val displayNameKey: String,
    override val displayNameArgs: List<Any> = emptyList(),
    afterProfile: BindingProfile? = null,
) : Command, KeyedCommand, ReportsFailure {
    private val before: ProjectActionSet = actions.current
    private val beforeProfile: BindingProfile = mappings.bindings
    private val afterProfile: BindingProfile = afterProfile ?: beforeProfile
    private val beforePath: String? = mappings.actionsPath
    private val afterPath: String = beforePath ?: ProjectActionSet.DEFAULT_PATH

    private var failure: String? = null

    fun isNoOp(): Boolean = after == before && afterProfile == beforeProfile && beforePath != null

    override fun execute() = restore(afterPath, after, afterProfile)

    override fun undo() = restore(beforePath, before, beforeProfile)

    private fun restore(path: String?, set: ProjectActionSet, profile: BindingProfile) {
        failure = null
        if (path != null) {
            try {
                write(path, set.toJson())
            } catch (e: Exception) {
                failure = "$path: ${e.message ?: e.javaClass.simpleName}"
                return
            }
        }
        mappings.actionsPath = path
        actions.apply(set)
        map.loadProfile(profile)
        mappings.bindings = profile
    }

    override fun wasSuccessful(): Boolean = failure == null
    override fun getFailureReason(): String? = failure
    override val errorTitleKey: String get() = "error.project_actions.save.title"

    override fun getDisplayName(): String = displayNameKey
    override fun getTargetName(): String = "window.keybindings"

    companion object {
        /**
         * Turns a Key Bindings [operation] into an edit of the asset when it changes a project action's bindings:
         * project actions keep their bindings as defaults in the asset, not as overrides. Returns null when no
         * project action is affected (use [EditBindingsCommand]), or the rejected [RebindResult] as [Baked.result].
         */
        fun bake(map: ActionMap, set: ProjectActionSet, profileName: String, operation: BindingOperation): Baked? {
            val scratch = map.copy()
            val result = operation.apply(scratch)
            val projectIds = map.definitions().filter { it.origin == ActionOrigin.PROJECT }.map { it.id }.toSet()
            if (result !is RebindResult.Success) return if (operation.touches(projectIds)) Baked(result, set, null) else null
            val changed = projectIds.filter { scratch.bindings(it) != map.bindings(it) }
            if (changed.isEmpty()) return null
            val baked = changed.fold(set) { s, id -> s.action(id)?.let { s.withAction(it.copy(defaults = scratch.bindings(id))) } ?: s }
            val profile = scratch.toProfile(operation.profileName(profileName))
            return Baked(result, baked, profile.copy(overrides = profile.overrides.filterKeys { it !in projectIds }))
        }

        private fun BindingOperation.touches(ids: Set<String>): Boolean = when (this) {
            is BindingOperation.Rebind -> id in ids
            is BindingOperation.Remove -> id in ids
            is BindingOperation.Clear -> id in ids
            else -> false
        }
    }

    data class Baked(val result: RebindResult, val set: ProjectActionSet, val profile: BindingProfile?)
}
