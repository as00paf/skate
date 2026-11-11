package com.pafoid.skate.editor.vcs

/**
 * EDT-79 §4.1: which changed files the Commit dialog has ticked, free of ImGui. New changes start ticked, like most
 * Git clients, except conflicted files (they cannot be committed); a file that is no longer changed drops out.
 */
class ChangeSelection {
    private val selected = linkedSetOf<String>()
    private var known = emptySet<String>()

    /** Follows a new read of the changes. */
    fun sync(entries: List<ChangeEntry>) {
        val paths = entries.map { it.path }.toSet()
        entries.filter { it.path !in known && it.change.kind != ChangeKind.CONFLICTING }.forEach { selected += it.path }
        selected.retainAll(paths)
        known = paths
    }

    fun isSelected(path: String): Boolean = path in selected

    fun toggle(path: String) {
        if (!selected.remove(path)) selected += path
    }

    /** True when every committable entry is ticked (and there is at least one). */
    fun allSelected(entries: List<ChangeEntry>): Boolean =
        committable(entries).let { list -> list.isNotEmpty() && list.all { it.path in selected } }

    /** Ticks every committable entry, or clears all when they already are. */
    fun toggleAll(entries: List<ChangeEntry>) {
        if (allSelected(entries)) selected.clear() else committable(entries).forEach { selected += it.path }
    }

    /** The paths to commit or discard: the ticked entries with their `.meta` files. */
    fun paths(entries: List<ChangeEntry>): List<String> = VcsStatusModel.pathsFor(entries, selected)

    private fun committable(entries: List<ChangeEntry>) = entries.filter { it.change.kind != ChangeKind.CONFLICTING }
}
