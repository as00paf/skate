package com.pafoid.skate.engine.ecs

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneSelectionTest {
    private val scene = Scene("s")
    private val a = GameObject("a")
    private val b = GameObject("b")
    private val c = GameObject("c")

    @Test
    fun SceneSelection_AddMakesLastAddedPrimary() {
        scene.addToSelection(a)
        scene.addToSelection(b)
        assertEquals(listOf(a, b), scene.selectedGameObjects)
        assertEquals(b, scene.selectedGameObject)
        assertTrue(scene.isSelected(a))
    }

    @Test
    fun SceneSelection_ReAddingMovesToPrimaryWithoutDuplicate() {
        scene.setSelection(listOf(a, b))
        scene.addToSelection(a)
        assertEquals(listOf(b, a), scene.selectedGameObjects)
    }

    @Test
    fun SceneSelection_RemoveUpdatesPrimary() {
        scene.setSelection(listOf(a, b, c))
        scene.removeFromSelection(c)
        assertEquals(b, scene.selectedGameObject)
        scene.removeFromSelection(a)
        assertEquals(listOf(b), scene.selectedGameObjects)
        assertFalse(scene.isSelected(a))
    }

    @Test
    fun SceneSelection_ClearAndNullAssignmentEmptySelection() {
        scene.setSelection(listOf(a, b))
        scene.clearSelection()
        assertTrue(scene.selectedGameObjects.isEmpty())
        assertNull(scene.selectedGameObject)

        scene.setSelection(listOf(a, b))
        scene.selectedGameObject = null
        assertTrue(scene.selectedGameObjects.isEmpty())
    }

    @Test
    fun SceneSelection_AssigningPrimaryReplacesSelection() {
        scene.setSelection(listOf(a, b))
        scene.selectedGameObject = c
        assertEquals(listOf(c), scene.selectedGameObjects)
    }

    @Test
    fun SceneSelection_ToggleAddsThenRemoves() {
        scene.toggleSelection(a)
        assertTrue(scene.isSelected(a))
        scene.toggleSelection(a)
        assertFalse(scene.isSelected(a))
    }
}
