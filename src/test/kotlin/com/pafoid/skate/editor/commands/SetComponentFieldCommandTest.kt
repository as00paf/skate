package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.SetComponentFieldCommand
import com.pafoid.skate.editor.events.ComponentFieldAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.ComponentFieldActionHandler
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.FollowCameraComponent
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-36: String fields edited in the generic inspector go through an undoable command. */
class SetComponentFieldCommandTest {

    private val targetField = FollowCameraComponent::class.java.getDeclaredField("targetName")

    @Test
    fun SetComponentField_ExecuteAndUndo() {
        val camera = FollowCameraComponent(targetName = "Skater")
        val command = SetComponentFieldCommand(camera, targetField, "Board")
        command.execute()
        assertEquals("Board", camera.targetName)
        command.undo()
        assertEquals("Skater", camera.targetName)
    }

    @Test
    fun SetComponentField_SameValueIsNoOp() {
        val camera = FollowCameraComponent(targetName = "Skater")
        assertTrue(SetComponentFieldCommand(camera, targetField, "Skater").isNoOp)
        assertFalse(SetComponentFieldCommand(camera, targetField, "Other").isNoOp)
    }

    @Test
    fun SetComponentField_UndoRestoresNull() {
        val animator = Animator()
        val field = Animator::class.java.getDeclaredField("controllerPath")
        val command = SetComponentFieldCommand(animator, field, "a.json")
        command.execute()
        assertEquals("a.json", animator.controllerPath)
        command.undo()
        assertNull(animator.controllerPath)
    }

    @Test
    fun FollowCameraTarget_ChangedFromInspectorEvent_IsUndoable() {
        val events = EventSystem()
        val undo = UndoRedoManager(events, mockk(relaxed = true))
        ComponentFieldActionHandler(events, undo)
        val camera = FollowCameraComponent(targetName = "Skater")

        events.publish(ComponentFieldAction.Set(camera, targetField, "Rider"))
        assertEquals("Rider", camera.targetName)

        undo.undo()
        assertEquals("Skater", camera.targetName)
        undo.redo()
        assertEquals("Rider", camera.targetName)
    }
}
