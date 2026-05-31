package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.editor.ClearLogsCommand
import com.pafoid.skate.editor.commands.editor.CopyToClipboardCommand
import com.pafoid.skate.editor.events.ConsoleAction
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Handles [ConsoleAction] events by executing the appropriate commands.
 *
 * All console UI actions (clear, copy) flow through this handler
 * instead of being executed directly in the UI layer.
 */
class ConsoleActionHandler : KoinComponent {

    private val eventSystem: EventSystem by inject()
    private val logger: LoggerService by inject()
    private val undoRedoManager: UndoRedoManager by inject()

    fun init() {
        eventSystem.subscribe<ConsoleAction.ClearLogs> {
            undoRedoManager.executeCommand(ClearLogsCommand(logger))
        }

        eventSystem.subscribe<ConsoleAction.CopyToClipboard> { event ->
            undoRedoManager.executeCommand(CopyToClipboardCommand(event.text))
        }
    }
}
