package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SelectionLogicTest {
    private val a = GameObject("A")
    private val b = GameObject("B")
    private val c = GameObject("C")
    private val d = GameObject("D")
    private val order = listOf(a, b, c, d)

    private fun names(list: List<GameObject>) = list.map { it.name }

    @Test
    fun HierarchyClick_PlainClickSelectsOnlyTheRow() {
        assertEquals(listOf("C"), names(SelectionLogic.hierarchyClick(listOf(a, b), c, order, a, ctrl = false, shift = false)))
    }

    @Test
    fun HierarchyClick_CtrlTogglesAndKeepsTheRest() {
        assertEquals(listOf("A", "C"), names(SelectionLogic.hierarchyClick(listOf(a), c, order, a, ctrl = true, shift = false)))
        assertEquals(listOf("C"), names(SelectionLogic.hierarchyClick(listOf(a, c), a, order, c, ctrl = true, shift = false)))
    }

    @Test
    fun HierarchyClick_ShiftSelectsTheRangeEitherWayWithTheClickedRowPrimary() {
        assertEquals(listOf("B", "C", "D"), names(SelectionLogic.hierarchyClick(listOf(b), d, order, b, ctrl = false, shift = true)))
        assertEquals(listOf("C", "B", "A"), names(SelectionLogic.hierarchyClick(listOf(c), a, order, c, ctrl = false, shift = true)))
        // No anchor: the range starts at the primary object.
        assertEquals(listOf("A", "B"), names(SelectionLogic.hierarchyClick(listOf(a), b, order, null, ctrl = false, shift = true)))
    }

    @Test
    fun HierarchyClick_CtrlShiftAddsTheRange() {
        assertEquals(listOf("A", "C", "D"), names(SelectionLogic.hierarchyClick(listOf(a, c), d, order, c, ctrl = true, shift = true)))
    }

    @Test
    fun HierarchyClick_ShiftOnARowOutsideTheListActsLikeAClick() {
        val hidden = GameObject("Hidden")
        assertEquals(listOf("Hidden"), names(SelectionLogic.hierarchyClick(listOf(a), hidden, order, a, ctrl = false, shift = true)))
    }

    @Test
    fun Anchor_ShiftKeepsItOtherClicksMoveIt() {
        assertEquals(a, SelectionLogic.anchorAfter(a, c, shift = true))
        assertEquals(c, SelectionLogic.anchorAfter(a, c, shift = false))
        assertEquals(c, SelectionLogic.anchorAfter(null, c, shift = true))
    }

    @Test
    fun ViewportClick_CtrlTogglesShiftAddsEmptySpaceClearsUnlessModified() {
        assertEquals(listOf("A", "B"), names(SelectionLogic.viewportClick(listOf(a), b, ctrl = true, shift = false)))
        assertEquals(listOf("B"), names(SelectionLogic.viewportClick(listOf(a, b), a, ctrl = true, shift = false)))
        assertEquals(listOf("B", "A"), names(SelectionLogic.viewportClick(listOf(a, b), a, ctrl = false, shift = true)))
        assertEquals(listOf("C"), names(SelectionLogic.viewportClick(listOf(a, b), c, ctrl = false, shift = false)))
        assertEquals(emptyList<String>(), names(SelectionLogic.viewportClick(listOf(a), null, ctrl = false, shift = false)))
        assertEquals(listOf("A"), names(SelectionLogic.viewportClick(listOf(a), null, ctrl = true, shift = false)))
    }

    @Test
    fun Roots_DropObjectsWhoseAncestorIsSelected() {
        val scene = Scene("S")
        val parent = GameObject("P").also { scene.addChild(it) }
        val child = GameObject("Child").also { parent.addChild(it) }
        val grandChild = GameObject("Grand").also { child.addChild(it) }
        val other = GameObject("Other").also { scene.addChild(it) }
        assertEquals(listOf("P", "Other"), names(SelectionLogic.roots(listOf(grandChild, parent, other, child))))
        assertEquals(listOf("Child"), names(SelectionLogic.roots(listOf(child, grandChild))))
    }

    @Test
    fun Targets_ActOnTheSelectionOnlyWhenTheTargetIsInIt() {
        val scene = Scene("S")
        listOf(a, b, c).forEach { scene.addChild(it) }
        scene.setSelection(listOf(a, b))
        assertEquals(listOf("A", "B"), names(SelectionLogic.targets(scene, b)))
        assertEquals(listOf("C"), names(SelectionLogic.targets(scene, c)))
        scene.setSelection(listOf(a))
        assertEquals(listOf("A"), names(SelectionLogic.targets(scene, a)))
        assertEquals(listOf("A"), names(SelectionLogic.targets(null, a)))
    }
}
