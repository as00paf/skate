package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.scene.CreateUiCommand
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UiKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CreateUiCommandTest {
    @Test
    fun CreateUi_ElementWithoutCanvasCreatesOneAndUndoRemovesBoth() {
        val scene = Scene()
        val cmd = CreateUiCommand(UiKind.BUTTON, scene, null)
        cmd.execute()
        val canvas = scene.children.single()
        assertTrue(canvas.getComponent<UICanvasComponent>() != null)
        assertEquals(1, canvas.children.size)
        assertEquals(canvas.children[0], scene.selectedGameObject)

        cmd.undo()
        assertTrue(scene.children.isEmpty())

        cmd.execute()
        assertEquals(1, scene.children.size)
    }

    @Test
    fun CreateUi_ElementGoesUnderSelectedUiParentAndReusesExistingCanvas() {
        val scene = Scene()
        CreateUiCommand(UiKind.CANVAS, scene, null).execute()
        val canvas = scene.children.single()
        CreateUiCommand(UiKind.PANEL, scene, null).execute()
        val panel = canvas.children.single()
        CreateUiCommand(UiKind.TEXT, scene, panel).execute()
        assertEquals(1, scene.children.size)
        assertEquals(1, panel.children.size)
    }
}
