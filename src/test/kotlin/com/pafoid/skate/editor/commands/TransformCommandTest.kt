package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.editor.events.TransformAction
import com.pafoid.skate.editor.gizmos.Gizmo
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.TransformActionHandler
import com.pafoid.skate.editor.imgui.components.TransformEditTracker
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TransformCommandTest {

    private val manager = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))

    private fun obj(name: String, x: Float = 0f) = GameObject(name).also { go ->
        val t = Transform(Vector3f(x, 0f, 0f))
        go.components.add(t)
        t.init(go)
    }

    private fun GameObject.t() = getComponent<Transform>()!!

    @Test
    fun gizmoStyleCommandWithLiveTransformRoundTripsUndoRedo() {
        val go = obj("Box")
        val start = TransformCommand.snapshot(go.t())
        go.t().translation.set(5f, 1f, 2f) // the drag, applied live
        go.t().rotation.set(0f, 90f, 0f)
        manager.pushCommand(TransformCommand(go, start, go.t())) // live Transform passed as "new"

        manager.undo()
        assertEquals(Vector3f(0f, 0f, 0f), go.t().translation)
        assertEquals(Vector3f(0f, 0f, 0f), go.t().rotation)

        manager.redo()
        assertEquals(Vector3f(5f, 1f, 2f), go.t().translation)
        assertEquals(Vector3f(0f, 90f, 0f), go.t().rotation)

        manager.undo()
        manager.redo()
        assertEquals(Vector3f(5f, 1f, 2f), go.t().translation)
    }

    @Test
    fun entrySnapshotsShareNoVectorsWithTheirSources() {
        val go = obj("Box")
        val entry = TransformCommand.Entry(go, go.t(), go.t())
        assertNotSame(go.t().translation, entry.old.translation)
        assertNotSame(go.t().scale, entry.new.scale)
        go.t().translation.set(9f, 9f, 9f)
        assertEquals(Vector3f(), entry.old.translation)
        assertEquals(Vector3f(), entry.new.translation)
    }

    @Test
    fun multiSelectionDragIsOneUndoStepThatRoundTrips() {
        val a = obj("A", 1f)
        val b = obj("B", 2f)
        val c = obj("C", 3f) // selected but not moved
        val snapshots = listOf(a, b, c).map { it to TransformCommand.snapshot(it.t()) }
        a.t().translation.add(10f, 0f, 0f)
        b.t().translation.add(10f, 0f, 0f)
        b.t().scale.set(2f, 2f, 2f)

        manager.pushCommand(Gizmo.buildCommand(snapshots)!!)
        assertEquals(1, manager.getUndoCount())

        manager.undo()
        assertEquals(1f, a.t().translation.x)
        assertEquals(2f, b.t().translation.x)
        assertEquals(Vector3f(1f, 1f, 1f), b.t().scale)
        assertEquals(3f, c.t().translation.x)

        manager.redo()
        assertEquals(11f, a.t().translation.x)
        assertEquals(12f, b.t().translation.x)
        assertEquals(Vector3f(2f, 2f, 2f), b.t().scale)
        assertEquals(3f, c.t().translation.x)
    }

    @Test
    fun gizmoBuildCommandIsNullWhenNothingMoved() {
        val a = obj("A")
        assertNull(Gizmo.buildCommand(listOf(a to TransformCommand.snapshot(a.t()))))
    }

    @Test
    fun inspectorEditEventIsRecordedAndRoundTrips() {
        val events = EventSystem()
        TransformActionHandler(events, manager, mockk(relaxed = true))
        val go = obj("Box")
        val start = TransformCommand.snapshot(go.t())
        go.t().scale.set(3f, 3f, 3f) // inspector edited the live vectors
        events.publish(TransformAction.Edited(TransformCommand.Entry(go, start, go.t())))

        assertEquals(1, manager.getUndoCount())
        assertEquals(Vector3f(3f, 3f, 3f), go.t().scale)
        manager.undo()
        assertEquals(Vector3f(1f, 1f, 1f), go.t().scale)
        manager.redo()
        assertEquals(Vector3f(3f, 3f, 3f), go.t().scale)
    }

    @Test
    fun sceneLoadedFromDiskSupportsGizmoAndInspectorUndoRedo() {
        val scene = Scene("Park")
        val board = obj("Skateboard").also(scene::addChild)
        obj("Visual").also(board::addChild)
        val serializer = Serializer()
        val reloaded = serializer.decode<Scene>(serializer.encode(scene))
        SceneManager(mockk(relaxed = true), EventSystem(), serializer, mockk(relaxed = true), mockk(relaxed = true))
            .resolveSceneReferences(reloaded) // what opening a saved scene does
        val loadedBoard = reloaded.children.single()
        val loadedVisual = loadedBoard.children.single()

        // Gizmo drag on the loaded root and child (multi-selection).
        val snapshots = listOf(loadedBoard, loadedVisual).map { it to TransformCommand.snapshot(it.t()) }
        loadedBoard.t().translation.set(2f, 0f, 0f)
        loadedVisual.t().rotation.set(0f, 45f, 0f)
        manager.pushCommand(Gizmo.buildCommand(snapshots)!!)

        // Inspector edit on the loaded child goes through the tracker and event like the real inspector.
        val events = EventSystem()
        TransformActionHandler(events, manager, mockk(relaxed = true))
        val tracker = TransformEditTracker()
        tracker.beforeDraw(loadedVisual.t())
        loadedVisual.t().scale.set(0.5f, 0.5f, 0.5f)
        tracker.afterDraw(loadedVisual.t().gameObject!!, loadedVisual.t(), interacting = false)
            .forEach { events.publish(TransformAction.Edited(it)) }

        assertEquals(2, manager.getUndoCount())
        manager.undo()
        manager.undo()
        assertEquals(Vector3f(), loadedBoard.t().translation)
        assertEquals(Vector3f(), loadedVisual.t().rotation)
        assertEquals(Vector3f(1f, 1f, 1f), loadedVisual.t().scale)
        manager.redo()
        manager.redo()
        assertEquals(Vector3f(2f, 0f, 0f), loadedBoard.t().translation)
        assertEquals(Vector3f(0f, 45f, 0f), loadedVisual.t().rotation)
        assertEquals(Vector3f(0.5f, 0.5f, 0.5f), loadedVisual.t().scale)
    }
}
