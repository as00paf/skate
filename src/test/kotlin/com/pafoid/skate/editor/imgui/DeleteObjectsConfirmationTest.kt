package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.ViewportActionHandler
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-62: deleting an object asks first, from every path; Cut does not; undo still restores. */
class DeleteObjectsConfirmationTest {

    private fun scene() = Scene("Main").apply {
        val parent = GameObject("Rail")
        val child = GameObject("Bolt").apply { addChild(GameObject("Nut")) }
        parent.addChild(child)
        parent.addChild(GameObject("Plate"))
        addChild(parent)
        addChild(GameObject("Box"))
    }

    private fun Scene.find(name: String): GameObject {
        fun walk(o: GameObject): GameObject? = if (o.name == name) o else o.children.firstNotNullOfOrNull { walk(it) }
        return walk(this)!!
    }

    @Test
    fun Confirmation_CountsEveryChildBelowTheObject() {
        val s = scene()
        assertEquals(3, DeleteObjectsConfirmation.descendantCount(s.find("Rail")))
        assertEquals(0, DeleteObjectsConfirmation.descendantCount(s.find("Box")))
    }

    @Test
    fun Confirmation_MessageNamesTheObjectAndItsChildren() {
        val s = scene()
        val c = DeleteObjectsConfirmation()
        c.request(s.find("Box"), s)
        assertEquals("dialog.delete_objects.one" to listOf<Any>("Box"), c.message())
        c.cancel()
        c.request(s.find("Rail"), s)
        assertEquals("dialog.delete_objects.one_with_children" to listOf<Any>("Rail", 3), c.message())
        c.request(s.find("Box"), s)
        assertEquals("dialog.delete_objects.many" to listOf<Any>(2, 3), c.message())
    }

    @Test
    fun Confirmation_DropsDuplicatesAndObjectsUnderARequestedParent() {
        val s = scene()
        val c = DeleteObjectsConfirmation()
        c.request(s.find("Nut"), s)
        c.request(s.find("Rail"), s) // replaces Nut: deleting Rail removes it anyway
        c.request(s.find("Bolt"), s) // already inside Rail
        c.request(s.find("Rail"), s)
        assertEquals(listOf("Rail"), c.pending.map { it.gameObject.name })
    }

    @Test
    fun Confirmation_ConfirmReturnsTheObjectsAndCancelDropsThem() {
        val s = scene()
        val c = DeleteObjectsConfirmation()
        c.request(s.find("Box"), s)
        c.cancel()
        assertTrue(c.pending.isEmpty())
        c.request(s.find("Box"), s)
        assertEquals(listOf("Box"), c.confirm().map { it.gameObject.name })
        assertTrue(c.pending.isEmpty())
    }

    // --- The delete paths, through the real event wiring ---

    private val events = EventSystem()
    private val objects = mockk<GameObjectManager>(relaxed = true)
    private val undo = UndoRedoManager(events, mockk(relaxed = true))
    private val dialog = DeleteObjectsDialog(events, mockk<StringManager>(relaxed = true))

    init {
        val engine = mockk<Engine>(relaxed = true) {
            every { eventSystem } returns events
            every { gameObjectManager } returns objects
        }
        ViewportActionHandler(engine, undo, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)).init()
    }

    @Test
    fun DeletePath_AsksFirstAndDeletesOnlyOnConfirm() {
        val s = scene()
        val box = s.find("Box")
        events.publish(ViewportAction.Delete(box, s)) // Hierarchy, Viewport menu, Delete key, Search Anywhere
        verify(exactly = 0) { objects.removeGameObject(any()) }
        assertEquals(listOf("Box"), dialog.confirmation.pending.map { it.gameObject.name })

        dialog.confirmation.confirm().forEach { events.publish(ViewportAction.DeleteConfirmed(it.gameObject, it.scene)) }
        verify(exactly = 1) { objects.removeGameObject(box) }

        undo.undo()
        verify { objects.addGameObject(box, any(), any()) }
    }

    @Test
    fun DeletePath_CancelDeletesNothing() {
        val s = scene()
        events.publish(ViewportAction.Delete(s.find("Rail"), s))
        dialog.confirmation.cancel()
        verify(exactly = 0) { objects.removeGameObject(any()) }
    }

    @Test
    fun DeletePath_CutDeletesWithoutAsking() {
        val s = scene()
        val box = s.find("Box")
        events.publish(ViewportAction.Delete(box, s, confirm = false))
        verify(exactly = 1) { objects.removeGameObject(box) }
        assertTrue(dialog.confirmation.pending.isEmpty())
    }
}
