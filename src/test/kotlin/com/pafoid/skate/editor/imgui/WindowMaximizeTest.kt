package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.WindowMaximize.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-88: the maximize state machine and what it asks of the layout (docs/design/EDT-88.md §2, §6, §8). */
class WindowMaximizeTest {

    private class FakeLayout : MaximizeLayout {
        val calls = mutableListOf<String>()
        var snapshots = 0
        override fun snapshot(): String = "ini${++snapshots}".also { calls += "snapshot" }
        override fun reload(snapshot: String) { calls += "reload $snapshot" }
        override fun dockOverViewport(window: String) { calls += "dock $window" }
        override fun focus(window: String) { calls += "focus $window" }
    }

    private val layout = FakeLayout()
    private val model = WindowMaximize(layout)

    @Test
    fun FullEditor_TouchesNoLayout_AndRestoreFocusesTheWindow() {
        model.toggle("window.script_editor", MaximizeMode.FULL_EDITOR)
        assertEquals(State.Full("window.script_editor"), model.state)
        assertEquals("window.script_editor", model.fullEditorWindow)
        assertFalse(model.holdsSnapshot)
        assertEquals(listOf("focus window.script_editor"), layout.calls)

        model.toggle("window.script_editor", MaximizeMode.FULL_EDITOR)
        assertEquals(State.None, model.state)
        assertEquals(listOf("focus window.script_editor", "focus window.script_editor"), layout.calls)
    }

    @Test
    fun OverViewport_SnapshotsThenDocks_AndRestoreReloadsTheSnapshot() {
        model.toggle("window.animation_editor", MaximizeMode.OVER_VIEWPORT)
        assertEquals(State.OverViewport("window.animation_editor", "ini1"), model.state)
        assertTrue(model.holdsSnapshot)
        assertNull(model.fullEditorWindow)
        assertEquals(listOf("snapshot", "dock window.animation_editor", "focus window.animation_editor"), layout.calls)

        layout.calls.clear()
        model.restore()
        assertEquals(State.None, model.state)
        assertEquals(listOf("reload ini1"), layout.calls, "the snapshot alone decides the front tabs")
    }

    @Test
    fun TogglingTheSameWindow_RestoresWhateverTheMode() {
        model.toggle("window.video_to_animation", MaximizeMode.OVER_VIEWPORT)
        model.toggle("window.video_to_animation", MaximizeMode.FULL_EDITOR)
        assertEquals(State.None, model.state)
    }

    @Test
    fun MaximizingAnotherWindow_OverViewport_KeepsTheFirstSnapshot() {
        model.toggle("window.script_editor", MaximizeMode.OVER_VIEWPORT)
        layout.calls.clear()
        model.toggle("window.animation_editor", MaximizeMode.OVER_VIEWPORT)
        assertEquals(State.OverViewport("window.animation_editor", "ini1"), model.state)
        assertEquals(1, layout.snapshots, "no snapshot of the temporary layout")
        assertEquals(listOf("reload ini1", "dock window.animation_editor", "focus window.animation_editor"), layout.calls)
    }

    @Test
    fun FromOverViewportToFullEditor_ReloadsTheSnapshot() {
        model.toggle("window.script_editor", MaximizeMode.OVER_VIEWPORT)
        layout.calls.clear()
        model.toggle("window.animation_editor", MaximizeMode.FULL_EDITOR)
        assertEquals(State.Full("window.animation_editor"), model.state)
        assertEquals(listOf("reload ini1", "focus window.animation_editor"), layout.calls)
    }

    @Test
    fun FromFullEditorToOverViewport_TakesASnapshot() {
        model.toggle("window.script_editor", MaximizeMode.FULL_EDITOR)
        model.toggle("window.animation_editor", MaximizeMode.OVER_VIEWPORT)
        assertEquals(State.OverViewport("window.animation_editor", "ini1"), model.state)
    }

    @Test
    fun Play_RestoresFullEditor_ButOnlyFocusesTheViewportOverIt() {
        model.toggle("window.script_editor", MaximizeMode.FULL_EDITOR)
        model.onPlay("window.game_viewport")
        assertEquals(State.None, model.state)

        model.toggle("window.script_editor", MaximizeMode.OVER_VIEWPORT)
        layout.calls.clear()
        model.onPlay("window.game_viewport")
        assertEquals("window.script_editor", model.window)
        assertEquals(listOf("focus window.game_viewport"), layout.calls)
    }

    @Test
    fun ClosingTheMaximizedWindow_Restores_OthersDoNot() {
        model.toggle("window.script_editor", MaximizeMode.OVER_VIEWPORT)
        model.onWindowClosed("window.console")
        assertEquals("window.script_editor", model.window)
        layout.calls.clear()
        model.onWindowClosed("window.script_editor")
        assertEquals(State.None, model.state)
        assertEquals(listOf("reload ini1"), layout.calls)
    }

    @Test
    fun ShowingAnotherWindow_RestoresOnlyTheFullEditor() {
        model.toggle("window.script_editor", MaximizeMode.FULL_EDITOR)
        model.onWindowShown("window.script_editor")
        assertEquals("window.script_editor", model.window)
        model.onWindowShown("window.console")
        assertEquals(State.None, model.state)

        model.toggle("window.script_editor", MaximizeMode.OVER_VIEWPORT)
        model.onWindowShown("window.console")
        assertEquals("window.script_editor", model.window, "over the viewport the other panels are visible anyway")
    }

    @Test
    fun ResetLayout_DropsTheStateWithoutReloading() {
        model.toggle("window.script_editor", MaximizeMode.OVER_VIEWPORT)
        layout.calls.clear()
        model.onResetLayout()
        assertEquals(State.None, model.state)
        assertTrue(layout.calls.isEmpty())
    }

    @Test
    fun Exit_ReturnsTheSnapshotToSave() {
        assertNull(model.onExit())
        model.toggle("window.script_editor", MaximizeMode.FULL_EDITOR)
        assertNull(model.onExit())
        assertEquals(State.None, model.state)
        model.toggle("window.script_editor", MaximizeMode.OVER_VIEWPORT)
        assertEquals("ini1", model.onExit())
        assertEquals(State.None, model.state)
    }

    @Test
    fun RestoreWithNothingMaximized_DoesNothing() {
        model.restore()
        assertTrue(layout.calls.isEmpty())
        assertNull(model.mode)
    }
}
