package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.CompositeCommand
import com.pafoid.skate.editor.commands.objects.EditComponentFieldsCommand
import com.pafoid.skate.editor.commands.objects.ResetComponentCommand
import com.pafoid.skate.editor.commands.objects.SetComponentFieldCommand
import com.pafoid.skate.editor.events.ComponentFieldAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager

/** Turns generic inspector edits into undoable [SetComponentFieldCommand]s and marks the scene dirty. */
class ComponentFieldActionHandler(
    eventSystem: EventSystem,
    undoRedoManager: UndoRedoManager,
    sceneManager: SceneManager? = null,
) {
    init {
        eventSystem.subscribe<ComponentFieldAction.Set> { event ->
            val command = SetComponentFieldCommand(event.component, event.field, event.value)
            if (command.isNoOp) return@subscribe
            undoRedoManager.executeCommand(command)
            sceneManager?.currentScene?.isDirty = true
        }
        eventSystem.subscribe<ComponentFieldAction.Edited> { event ->
            val command = EditComponentFieldsCommand(event.component, event.changes)
            if (event.mirrors.isEmpty()) {
                if (!command.isNoOp) undoRedoManager.executeCommand(command)
                return@subscribe
            }
            // EDT-74: the primary edit plus its copies on the other selected objects, as one undo step.
            val steps = listOf(command) + event.mirrors.map { (c, changes) -> EditComponentFieldsCommand(c, changes) }
            undoRedoManager.executeCommand(CompositeCommand("command.CompositeCommand.edit", "Edit", steps.map { s -> { s } }))
        }
        eventSystem.subscribe<ComponentFieldAction.Reset> { event -> undoRedoManager.executeCommand(ResetComponentCommand(event.component)) }
    }
}
