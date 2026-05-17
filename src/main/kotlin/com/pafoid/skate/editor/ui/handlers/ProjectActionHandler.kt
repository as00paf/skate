package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.project.CreateFileCommand
import com.pafoid.skate.editor.commands.project.DeleteFileCommand
import com.pafoid.skate.editor.commands.project.RenameFileCommand
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
            undoRedoManager.executeCommand(CreateFileCommand(event.path, event.isDirectory, logger))
            eventSystem.publish(FileSystemEvent.FileSystemChangedEvent(event.path))
        }
        eventSystem.subscribe<RenameFileRequested> { event ->
            undoRedoManager.executeCommand(RenameFileCommand(event.path, event.newName, logger))
            eventSystem.publish(FileSystemEvent.FileSystemChangedEvent(event.path))
        }
        eventSystem.subscribe<DeleteFileRequested> { event ->
            undoRedoManager.executeCommand(DeleteFileCommand(event.path, logger))
            eventSystem.publish(FileSystemEvent.FileSystemChangedEvent(event.path))
        }
    }
}
