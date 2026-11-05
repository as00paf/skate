package com.pafoid.skate.editor.ui.handlers

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
    }
}
