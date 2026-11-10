package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene

/**
 * EDT-74: multi-selection rules, free of ImGui. A selection is an ordered list whose last element is the primary
 * object (the one Properties and the gizmo pivot follow), like [Scene.selectedGameObjects].
 */
object SelectionLogic {

    /**
     * The Hierarchy's selection after clicking [clicked]. [order] is the visible tree, top to bottom; [anchor] is
     * where the last plain or Ctrl click landed (the start of a Shift range).
     * - Plain click: only [clicked].
     * - Ctrl: toggles [clicked]; removing it makes the previous object primary.
     * - Shift: the range from [anchor] to [clicked] replaces the selection; Ctrl+Shift adds the range to it.
     */
    fun hierarchyClick(
        current: List<GameObject>,
        clicked: GameObject,
        order: List<GameObject>,
        anchor: GameObject?,
        ctrl: Boolean,
        shift: Boolean,
    ): List<GameObject> {
        if (shift) {
            val from = order.indexOf(anchor ?: current.lastOrNull())
            val to = order.indexOf(clicked)
            if (from >= 0 && to >= 0) {
                val range = if (from <= to) order.subList(from, to + 1) else order.subList(to, from + 1).asReversed()
                // The clicked object ends the list, so it becomes primary.
                val ordered = range.filter { it !== clicked } + clicked
                return if (ctrl) current.filter { it !in ordered } + ordered else ordered
            }
        }
        if (ctrl) return toggle(current, clicked)
        return listOf(clicked)
    }

    /** The Hierarchy anchor after a click: Shift keeps the old one, any other click moves it to [clicked]. */
    fun anchorAfter(anchor: GameObject?, clicked: GameObject, shift: Boolean): GameObject =
        if (shift && anchor != null) anchor else clicked

    /**
     * The selection after a viewport click on [hit] (null: empty space). Ctrl toggles, Shift adds, a plain click
     * replaces. Clicking empty space clears the selection, unless a modifier is held.
     */
    fun viewportClick(current: List<GameObject>, hit: GameObject?, ctrl: Boolean, shift: Boolean): List<GameObject> = when {
        hit == null -> if (ctrl || shift) current else emptyList()
        ctrl -> toggle(current, hit)
        shift -> current.filter { it !== hit } + hit
        else -> listOf(hit)
    }

    private fun toggle(current: List<GameObject>, go: GameObject): List<GameObject> =
        if (current.any { it === go }) current.filter { it !== go } else current + go

    /**
     * [selection] without the objects that have a selected ancestor, in order. Moving, deleting or copying an
     * ancestor already takes its children along, so acting on both would do it twice.
     */
    fun roots(selection: List<GameObject>): List<GameObject> {
        val set = selection.toSet()
        return selection.filter { go -> go.ancestors().none { it in set } }.distinct()
    }

    /**
     * The objects an action on [target] applies to: the whole selection ([roots]) when [target] is part of a
     * multi-selection, else just [target]. So a right-click or shortcut on a selected row acts on every selected
     * object, and a right-click on an unselected row acts on that row only.
     */
    fun targets(scene: Scene?, target: GameObject): List<GameObject> {
        val selected = scene?.selectedGameObjects.orEmpty()
        return if (selected.size > 1 && selected.any { it === target }) roots(selected) else listOf(target)
    }
}
