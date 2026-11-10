package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.UiRectCommand
import com.pafoid.skate.editor.settings.GizmoSnapSettings
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UiDragStart
import com.pafoid.skate.engine.ui.UiEditing
import com.pafoid.skate.engine.ui.UiHandle
import com.pafoid.skate.engine.ui.UiRectF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** EDT-75: one viewport drag (rect, pivot or anchors) is one undo step that restores everything it changed. */
class UiRectCommandTest {
    private val parent = UiRectF(0f, 0f, 1000f, 500f)

    private fun state(e: UIElementComponent) =
        listOf(e.x, e.y, e.width, e.height, e.pivotX, e.pivotY, e.anchorMinX, e.anchorMinY, e.anchorMaxX, e.anchorMaxY)

    @Test
    fun UiRectCommand_UndoRestoresRectPivotAndAnchors() {
        val e = UIElementComponent().placed(0.5f, 0.5f, 10f, 20f, 200f, 100f).also { it.rect = UiRectF(410f, 220f, 200f, 100f) }
        val original = state(e)
        val before = UiRectCommand.Rect.of(e)
        val start = UiDragStart(e.rect, parent, e.width, e.height, 0f, 0f, false)
        UiEditing.drag(e, start, UiHandle.ANCHOR_MIN, 0f, 0f)
        UiEditing.drag(e, start, UiHandle.PIVOT, 410f, 220f)
        val edited = state(e)
        val cmd = UiRectCommand(GameObject("Button"), e, before, UiRectCommand.Rect.of(e))

        cmd.undo()
        assertEquals(original, state(e))
        cmd.execute()
        assertEquals(edited, state(e))
    }

    @Test
    fun UiSnapStep_DefaultsAndIsClamped() {
        assertEquals(GizmoSnapSettings.DEFAULT_UI, GizmoSnapSettings().ui)
        assertEquals(GizmoSnapSettings.MIN_UI, GizmoSnapSettings(ui = 0f).sanitized().ui)
        assertEquals(GizmoSnapSettings.MAX_UI, GizmoSnapSettings(ui = 9999f).sanitized().ui)
    }
}
