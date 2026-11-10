package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.editor.events.TransformAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager

class TransformActionHandler(eventSystem: EventSystem, undoRedoManager: UndoRedoManager, sceneManager: SceneManager) {
    init {
        eventSystem.subscribe<TransformAction.Edited> { event ->
            // Execute re-applies the new state (a no-op after an inspector edit) and records it.
            undoRedoManager.executeCommand(TransformCommand(listOf(event.entry) + event.mirrors))
            sceneManager.currentScene?.isDirty = true
        }
    }
}
