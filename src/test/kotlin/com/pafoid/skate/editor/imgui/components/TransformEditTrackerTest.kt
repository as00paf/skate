package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransformEditTrackerTest {

    private val tracker = TransformEditTracker()

    private fun obj(name: String) = GameObject(name).also { go -> Transform().also { go.components.add(it); it.init(go) } }
    private fun GameObject.t() = components.filterIsInstance<Transform>().first()

    /** One inspector frame: widgets apply [edit] while [interacting]. */
    private fun frame(go: GameObject, interacting: Boolean, edit: (Transform) -> Unit = {}) =
        tracker.beforeDraw(go.t()).let { edit(go.t()); tracker.afterDraw(go, go.t(), interacting) }

    @Test
    fun multiFrameDragBecomesOneEntryFromStartToEnd() {
        val go = obj("Box")
        assertTrue(frame(go, true) { it.translation.x = 1f }.isEmpty())
        assertTrue(frame(go, true) { it.translation.x = 2f }.isEmpty())
        assertTrue(frame(go, true) { it.translation.x = 3f }.isEmpty())
        val done = frame(go, false).single()
        assertEquals(0f, done.old.translation.x)
        assertEquals(3f, done.new.translation.x)
    }

    @Test
    fun resetButtonClickWithMouseUpCommitsImmediately() {
        val go = obj("Box")
        go.t().rotation.set(10f, 20f, 30f)
        val done = frame(go, false) { it.rotation.x = 0f }.single()
        assertEquals(Vector3f(10f, 20f, 30f), done.old.rotation)
        assertEquals(Vector3f(0f, 20f, 30f), done.new.rotation)
    }

    @Test
    fun noChangeOrDragBackToStartRecordsNothing() {
        val go = obj("Box")
        assertTrue(frame(go, false).isEmpty())
        frame(go, true) { it.scale.x = 2f }
        frame(go, true) { it.scale.x = 1f }
        assertTrue(frame(go, false).isEmpty())
    }

    @Test
    fun outsideChangesBetweenFramesAreNotRecorded() {
        val go = obj("Box")
        go.t().translation.x = 7f // gizmo drag or undo between inspector frames
        assertTrue(frame(go, false).isEmpty())
    }

    @Test
    fun selectionChangeMidEditFlushesThePendingEdit() {
        val a = obj("A")
        val b = obj("B")
        frame(a, true) { it.translation.y = 4f }
        val done = frame(b, true)
        assertEquals(a, done.single().gameObject)
        assertEquals(4f, done.single().new.translation.y)
    }
}
