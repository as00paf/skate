package com.pafoid.skate.editor.vcs

import com.pafoid.skate.editor.imgui.data.Theme
import org.joml.Vector4f

/**
 * EDT-79 Q7: the graph column of the Git log. Commits come newest first (as `git log --all`). For each row the layout
 * gives the lane of the commit's dot and three sets of lanes, so the window only draws a circle and straight lines:
 * - [Row.up]: lanes coming from the row's top edge into the dot (the children's lines);
 * - [Row.down]: lanes going from the dot to the row's bottom edge (one per parent);
 * - [Row.through]: lanes crossing the row from top to bottom without touching the dot.
 * A lane index means the same horizontal position at the bottom of one row and the top of the next.
 *
 * It is the usual "active lanes" walk: every lane waits for one commit id. A commit takes the leftmost lane waiting
 * for it (the other waiting lanes end in its dot). Its first parent continues in that lane; every other parent joins
 * the lane already waiting for it or takes a free one. Freed lanes are reused.
 */
object CommitGraph {
    data class Row(val lane: Int, val up: List<Int>, val down: List<Int>, val through: List<Int>) {
        /** Number of lane positions this row needs. */
        val width: Int get() = (listOf(lane) + up + down + through).max() + 1
    }

    fun layout(commits: List<VcsCommit>): List<Row> {
        val known = commits.map { it.id }.toSet()
        val lanes = mutableListOf<String?>() // lane -> commit id it waits for
        return commits.map { commit ->
            val up = lanes.indices.filter { lanes[it] == commit.id }
            val through = lanes.indices.filter { lanes[it] != null && it !in up }
            val lane = up.firstOrNull() ?: freeLane(lanes)
            up.forEach { lanes[it] = null }
            val down = mutableListOf<Int>()
            commit.parents.filter { it in known }.forEachIndexed { i, parent ->
                // The first parent keeps the commit's lane (the line goes on straight, even when another lane also
                // waits for that parent: both then join in its dot). Other parents join a lane already waiting for
                // them, else take a free one.
                val existing = lanes.indexOf(parent)
                val target = when {
                    i == 0 -> lane
                    existing >= 0 -> existing
                    else -> freeLane(lanes)
                }
                lanes.setOrAdd(target, parent)
                down += target
            }
            while (lanes.isNotEmpty() && lanes.last() == null) lanes.removeAt(lanes.size - 1)
            Row(lane, up, down.distinct(), through)
        }
    }

    private fun freeLane(lanes: List<String?>): Int = lanes.indexOfFirst { it == null }.let { if (it < 0) lanes.size else it }

    private fun MutableList<String?>.setOrAdd(index: Int, value: String) {
        while (size <= index) add(null)
        this[index] = value
    }
}

/** EDT-79: the Git log graph's colours, all from the theme (lanes cycle through a theme palette). */
object GraphStyle {
    fun palette(theme: Theme): List<Vector4f> = listOf(theme.accent, theme.success, theme.danger, theme.accentHover, theme.textDisabled)

    fun laneColor(lane: Int, theme: Theme): Vector4f = palette(theme).let { it[Math.floorMod(lane, it.size)] }

    /** A `[branch]` tag (and the dot of the commit it points at): the current branch stands out. */
    fun tagColor(current: Boolean, theme: Theme): Vector4f = if (current) theme.warning else theme.accent
}

/** EDT-79 Q7: the Git log's filters, a branch and a text, free of ImGui. */
object GitLogFilter {
    /** The commits reachable from [tip] by parent links, within the loaded [commits]. */
    fun reachable(commits: List<VcsCommit>, tip: String): Set<String> {
        val byId = commits.associateBy { it.id }
        val seen = mutableSetOf<String>()
        val stack = ArrayDeque(listOf(tip))
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (!seen.add(id)) continue
            byId[id]?.parents?.forEach { stack.addLast(it) }
        }
        return seen.filterTo(mutableSetOf()) { it in byId }
    }

    /**
     * The commits to list: those reachable from [branch]'s tip (null: every branch), whose message, author or id
     * contains [text] (case-insensitive). The graph is laid out on the branch-filtered list, so it stays connected.
     */
    fun apply(commits: List<VcsCommit>, branches: VcsBranches?, branch: String?, text: String): List<VcsCommit> {
        val tip = branch?.let { branches?.tips?.get(it) }
        val onBranch = if (branch == null) commits else if (tip == null) emptyList() else reachable(commits, tip).let { ids -> commits.filter { it.id in ids } }
        val q = text.trim().lowercase()
        if (q.isEmpty()) return onBranch
        return onBranch.filter { c -> c.message.lowercase().contains(q) || c.author.lowercase().contains(q) || c.id.startsWith(q) }
    }

    /** Branch names pointing at each commit id, for the `[name]` labels in the graph column. */
    fun labels(branches: VcsBranches?): Map<String, List<String>> =
        branches?.tips.orEmpty().entries.groupBy({ it.value }, { it.key }).mapValues { (_, names) -> names.sorted() }
}

/**
 * EDT-79 §4.3: the Conflicts dialog opens by itself when a refresh first sees conflicts, and not again for files the
 * user was already shown (resolving one of them leaves the rest; that is no reason to reopen).
 */
class ConflictPrompt {
    private var offered: Set<String> = emptySet()

    /** True when the dialog should open now for [conflicts]. */
    fun shouldOpen(conflicts: Collection<String>): Boolean {
        val now = conflicts.toSet()
        if (now.isEmpty()) {
            offered = emptySet()
            return false
        }
        val fresh = !offered.containsAll(now)
        offered = offered + now
        return fresh
    }
}
