package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.imgui.DeleteObjectsDialog
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.getComponent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-74: delete, duplicate, cut/copy/paste and reparent act on the whole selection, as one undo step. */
class MultiSelectionActionsTest {
    private val events = EventSystem()
    private val undo = UndoRedoManager(events, mockk(relaxed = true))
    private val scene = Scene("Main")
    private val clipboard = mockk<ClipboardService>(relaxed = true)

    private fun obj(name: String, x: Float = 0f, parent: GameObject = scene) =
        GameObject(name).also { it.addComponent(Transform(translation = Vector3f(x, 0f, 0f))); parent.addChild(it) }

    private val a = obj("A", 1f)
    private val b = obj("B", 3f)
    private val c = obj("C", 5f)
    private val target = obj("Target")
    private val bChild = obj("BChild", parent = b)

    init {
        val sceneManager = mockk<SceneManager>(relaxed = true) { every { currentScene } returns scene }
        val engine = mockk<Engine>(relaxed = true) {
            every { eventSystem } returns events
            every { this@mockk.sceneManager } returns sceneManager
            every { gameObjectManager } returns GameObjectManager().also { it.init(scene) }
        }
        ViewportActionHandler(engine, undo, clipboard, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)).init()
    }

    private fun names(list: List<GameObject>) = list.map { it.name }

    @Test
    fun SelectionChanged_ReplacesTheSelection() {
        events.publish(ViewportAction.MultiSelectionChanged(listOf(a, c)))
        assertEquals(listOf(a, c), scene.selectedGameObjects)
        assertEquals(c, scene.selectedGameObject)
    }

    @Test
    fun Delete_ConfirmsTheWholeSelectionAndUndoesInOneStep() {
        val dialog = DeleteObjectsDialog(events, mockk<StringManager>(relaxed = true))
        scene.setSelection(listOf(a, b, bChild))
        events.publish(ViewportAction.Delete(a, scene))
        assertEquals(listOf("A", "B"), dialog.confirmation.pending.map { it.gameObject.name }) // BChild goes with B
        dialog.confirmation.confirm().let { events.publish(ViewportAction.DeleteConfirmed(it.map { r -> r.gameObject }, scene)) }
        assertEquals(listOf("C", "Target"), names(scene.children))
        assertEquals(1, undo.getUndoCount())
        undo.undo()
        assertEquals(listOf("A", "B", "C", "Target"), names(scene.children))
        assertEquals(listOf(bChild), b.children)
    }

    @Test
    fun Delete_OnAnUnselectedObjectDeletesOnlyIt() {
        scene.setSelection(listOf(a, b))
        events.publish(ViewportAction.Delete(c, scene, confirm = false))
        assertEquals(listOf("A", "B", "Target"), names(scene.children))
    }

    @Test
    fun Duplicate_CopiesEverySelectedObjectAndSelectsTheCopies() {
        scene.setSelection(listOf(a, c))
        events.publish(ViewportAction.Duplicate(c))
        assertEquals(listOf("A (Copy)", "C (Copy)"), names(scene.selectedGameObjects))
        assertEquals(6, scene.children.size)
        assertEquals(1, undo.getUndoCount())
        undo.undo()
        assertEquals(4, scene.children.size)
        assertEquals(listOf(a, c), scene.selectedGameObjects)
    }

    @Test
    fun Reparent_DraggingASelectedRowMovesTheSelection() {
        scene.setSelection(listOf(a, b, bChild))
        events.publish(ViewportAction.Reparent(b, target))
        assertEquals(listOf("A", "B"), names(target.children))
        assertEquals(listOf(bChild), b.children)
        assertEquals(1, undo.getUndoCount())
        undo.undo()
        assertEquals(listOf("A", "B", "C", "Target"), names(scene.children))
    }

    @Test
    fun Reparent_SkipsAMoveIntoItself() {
        scene.setSelection(listOf(a, target))
        events.publish(ViewportAction.Reparent(a, target))
        assertEquals(listOf("A"), names(target.children))
        assertEquals(1, undo.getUndoCount())
    }

    @Test
    fun Copy_TakesTheSelectionRoots() {
        val copied = slot<List<GameObject>>()
        every { clipboard.copy(capture(copied)) } returns Unit
        scene.setSelection(listOf(b, bChild, c))
        events.publish(ViewportAction.CopyClipboard(c))
        assertEquals(listOf("B", "C"), names(copied.captured))
    }

    @Test
    fun Paste_AddsEveryObjectKeepingTheirOffsets() {
        val p = GameObject("P").apply { addComponent(Transform(translation = Vector3f(10f, 0f, 0f))) }
        val q = GameObject("Q").apply { addComponent(Transform(translation = Vector3f(12f, 0f, 0f))) }
        every { clipboard.pasteAll() } returns listOf(p, q)
        events.publish(ViewportAction.PasteClipboard())
        assertEquals(0f, p.getComponent<Transform>()!!.translation.x)
        assertEquals(2f, q.getComponent<Transform>()!!.translation.x)
        assertEquals(listOf(p, q), scene.selectedGameObjects)
        assertEquals(1, undo.getUndoCount())
        undo.undo()
        assertTrue(p.parent == null && q.parent == null)
        verify(exactly = 1) { clipboard.pasteAll() }
    }
}
