package com.pafoid.skate.editor.vcs

/**
 * EDT-79 §3: the branch popup's entries and their enabling rules, free of ImGui. The rules reuse
 * [VcsStatusModel.actionRefusal], so the popup, the dialogs and `VcsActionHandler` agree on what is refused.
 */
object BranchPopupModel {
    enum class Action { UPDATE, COMMIT, CHANGES, RESOLVE, HISTORY, NEW_BRANCH, INITIALIZE, CLONE }

    /** One action row. [reasonKey] says why a disabled row is disabled (shown as its tooltip). */
    data class Entry(val action: Action, val enabled: Boolean, val reasonKey: String? = null, val count: Int = 0)

    data class BranchRow(val name: String, val current: Boolean)

    /** The action rows for [state], top to bottom (EDT-51 §2.3 order: actions first, destructive last). */
    fun actions(state: VcsSession.State, playing: Boolean): List<Entry> {
        val busy = state.busy
        fun gate(action: Action, count: Int = 0, refusal: String? = null) =
            when {
                busy -> Entry(action, false, "vcs.busy", count)
                refusal != null -> Entry(action, false, refusal, count)
                else -> Entry(action, true, null, count)
            }
        if (!state.isRepository) {
            return listOf(
                gate(Action.INITIALIZE, refusal = VcsStatusModel.actionRefusal(false, playing, false)),
                Entry(Action.CLONE, false, "vcs.popup.needs_remote"),
            )
        }
        val playRefusal = VcsStatusModel.actionRefusal(false, playing, false)
        return buildList {
            add(Entry(Action.UPDATE, false, "vcs.popup.needs_remote"))
            add(gate(Action.COMMIT, state.entries.count { it.change.kind != ChangeKind.CONFLICTING }, playRefusal))
            add(Entry(Action.CHANGES, true))
            if (state.hasConflicts) add(Entry(Action.RESOLVE, true, count = state.conflicts.size))
            add(Entry(Action.HISTORY, true))
            add(gate(Action.NEW_BRANCH, refusal = playRefusal ?: "vcs.error.no_commits".takeIf { state.history.isEmpty() }))
        }
    }

    /** The local branches matching [query] (case-insensitive substring), the current branch first. */
    fun branches(state: VcsSession.State, query: String): List<BranchRow> {
        val b = state.branches ?: return emptyList()
        val q = query.trim().lowercase()
        return b.local.filter { q.isEmpty() || it.lowercase().contains(q) }
            .sortedWith(compareBy<String> { it != b.current }.thenBy { it.lowercase() })
            .map { BranchRow(it, it == b.current) }
    }

    /** Enter in the search box checks out the branch when exactly one other branch matches. */
    fun enterTarget(rows: List<BranchRow>): String? = rows.singleOrNull()?.takeIf { !it.current }?.name

    /** Why checking out another branch is refused right now, or null (the handler checks again). */
    fun checkoutRefusal(state: VcsSession.State, playing: Boolean, unsavedScenes: Boolean): String? = when {
        state.busy -> "vcs.busy"
        else -> VcsStatusModel.actionRefusal(true, playing, unsavedScenes)
    }

    /** Why deleting [branch] is refused, or null. JGit still refuses unmerged branches when it runs. */
    fun deleteRefusal(state: VcsSession.State, branch: String, playing: Boolean): String? = when {
        state.busy -> "vcs.busy"
        branch == state.branches?.current -> "vcs.popup.delete_current"
        else -> VcsStatusModel.actionRefusal(false, playing, false)
    }
}

/**
 * EDT-79 §4.5: branch-name rules checked live in the New Branch modal. They follow `git check-ref-format` closely
 * enough for a hint; JGit's own check still runs when the branch is created.
 */
object BranchNames {
    private val FORBIDDEN = Regex("""[\s~^:?*\[\\\u0000-\u001f\u007f]""")

    /** Null when [name] is acceptable, else the string key of what is wrong. */
    fun problem(name: String, existing: Collection<String>): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "vcs.branch.name.empty"
            n in existing -> "vcs.branch.name.exists"
            FORBIDDEN.containsMatchIn(n) || n.contains("..") || n.contains("@{") || n == "@" ||
                n.startsWith("/") || n.endsWith("/") || n.contains("//") || n.startsWith("-") ||
                n.endsWith(".") || n.endsWith(".lock") || n.split('/').any { it.startsWith(".") } -> "vcs.branch.name.invalid"
            else -> null
        }
    }
}
