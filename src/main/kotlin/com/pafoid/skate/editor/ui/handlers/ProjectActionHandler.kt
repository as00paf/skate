package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.project.CreateFileCommand
import com.pafoid.skate.editor.commands.project.DeleteFileCommand
import com.pafoid.skate.editor.commands.project.RenameFileCommand
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.events.CreateFileRequested
import com.pafoid.skate.editor.events.DeleteFileRequested
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.events.RenameFileRequested
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem

class ProjectActionHandler(
    private val undoRedoManager: UndoRedoManager,
    private val logger: LoggerService,
    private val eventSystem: EventSystem,
) {
    fun init() {
        eventSystem.subscribe<CreateFileRequested> { event ->
            val command = CreateFileCommand(event.path, event.isDirectory, logger)
            executeAndPublishOnSuccess(command, event.path)
        }
        eventSystem.subscribe<RenameFileRequested> { event ->
            val command = RenameFileCommand(event.path, event.newName, logger)
            executeAndPublishOnSuccess(command, event.path)
        }
        eventSystem.subscribe<DeleteFileRequested> { event ->
            val command = DeleteFileCommand(event.path, logger)
            executeAndPublishOnSuccess(command, event.path)
        }
    }

    private fun executeAndPublishOnSuccess(command: ExecutionTrackedCommand, affectedPath: String) {
        undoRedoManager.executeCommand(command)
        if (!command.wasSuccessful()) {
            return
        }
        eventSystem.publish(FileSystemEvent.FileSystemChangedEvent(affectedPath))
    }
}
