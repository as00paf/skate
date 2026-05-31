package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.events.UndoRedoAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Handles [UndoRedoAction] events published by the [CommandHistoryWindow].
 *
 * Decouples the command history UI from direct calls to [UndoRedoManager],
 * following the event-driven architecture pattern.
 */
class UndoRedoActionHandler : KoinComponent {

    private val eventSystem: EventSystem by inject()
    private val undoRedoManager: UndoRedoManager by inject()

    fun init() {
        eventSystem.subscribe<UndoRedoAction.Undo> {
            undoRedoManager.undo()
        }

        eventSystem.subscribe<UndoRedoAction.Redo> {
            undoRedoManager.redo()
        }

        eventSystem.subscribe<UndoRedoAction.UndoTo> { event ->
            undoRedoManager.undoTo(event.targetSize)
        }

        eventSystem.subscribe<UndoRedoAction.RedoTo> { event ->
            undoRedoManager.redoTo(event.targetSize)
        }

        eventSystem.subscribe<UndoRedoAction.ClearHistory> {
            undoRedoManager.clear()
        }
    }
}
