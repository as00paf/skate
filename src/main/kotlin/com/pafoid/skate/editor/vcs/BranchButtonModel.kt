package com.pafoid.skate.editor.vcs

/**
 * EDT-79 §2: what the status-bar branch button shows for a [VcsSession.State]. Pure, so every label state is tested;
 * the button only draws [Label].
 */
object BranchButtonModel {
    enum class Tone { NORMAL, DIM, WARNING, DANGER }

    /** What a click does: the branch popup, or the Conflicts dialog directly while conflicts exist. */
    enum class Click { POPUP, CONFLICTS }

    data class Label(
        /** String key of the text after the branch icon, formatted with [args]. */
        val textKey: String,
        val args: List<Any> = emptyList(),
        val tone: Tone = Tone.NORMAL,
        val tooltipKey: String,
        val tooltipArgs: List<Any> = emptyList(),
        val click: Click = Click.POPUP,
    )

    /** The label, or null to hide the button (no project open). */
    fun label(state: VcsSession.State, hasProject: Boolean): Label? {
        if (!hasProject || state.root == null) return null
        if (!state.loaded) return Label("vcs.button.loading", tone = Tone.DIM, tooltipKey = "vcs.loading")
        if (!state.isRepository) return Label("vcs.button.off", tone = Tone.DIM, tooltipKey = "vcs.button.off.tooltip")
        val name = branchName(state)
        val conflicts = state.conflicts.size
        val changed = state.entries.size
        return when {
            state.busy -> Label("vcs.button.busy", listOf(name), Tone.DIM, "vcs.busy")
            conflicts > 0 -> Label(
                "vcs.button.conflicts", listOf(name, conflicts), Tone.DANGER, "vcs.button.conflicts.tooltip", listOf(conflicts),
                click = Click.CONFLICTS,
            )
            state.error != null -> Label(
                "vcs.button.error", listOf(name), Tone.WARNING, state.error.messageKey, state.error.args.toList(),
            )
            state.branches?.detached == true -> Label("vcs.button.clean", listOf(name), tooltipKey = "vcs.button.detached.tooltip", tooltipArgs = listOf(name))
            changed > 0 -> Label("vcs.button.changes", listOf(name, changed), tooltipKey = "vcs.button.changes.tooltip", tooltipArgs = listOf(name, changed))
            else -> Label("vcs.button.clean", listOf(name), tooltipKey = "vcs.button.clean.tooltip", tooltipArgs = listOf(name))
        }
    }

    /** The current branch, or the short commit id when HEAD is detached, or `?` before anything was read. */
    fun branchName(state: VcsSession.State): String {
        val b = state.branches ?: return "?"
        return b.current ?: b.head?.take(7) ?: "?"
    }
}
