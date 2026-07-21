package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.editor.ClearLogsCommand
import com.pafoid.skate.editor.commands.editor.CopyToClipboardCommand
import com.pafoid.skate.editor.events.ConsoleAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService

/**
 * Handles [ConsoleAction] events by executing the appropriate commands.
 *
 * All console UI actions (clear, copy) flow through this handler
 * instead of being executed directly in the UI layer.
 */
class ConsoleActionHandler(
    private val eventSystem: EventSystem,
    private val logger: LoggerService,
    private val undoRedoManager: UndoRedoManager,
) {
    init {
        eventSystem.subscribe<ConsoleAction.ClearLogs> {
            undoRedoManager.executeCommand(ClearLogsCommand(logger))
        }

        eventSystem.subscribe<ConsoleAction.CopyToClipboard> { event ->
            undoRedoManager.executeCommand(CopyToClipboardCommand(event.text))
        }
    }
}
