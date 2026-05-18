package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.project.CreateFileCommand
import com.pafoid.skate.editor.commands.project.CloseProjectCommand
import com.pafoid.skate.editor.commands.project.CreateProjectCommand
import com.pafoid.skate.editor.commands.project.DeleteFileCommand
import com.pafoid.skate.editor.commands.project.LoadLastProjectCommand
import com.pafoid.skate.editor.commands.project.OpenProjectCommand
import com.pafoid.skate.editor.commands.project.RenameFileCommand
import com.pafoid.skate.editor.commands.project.SaveProjectCommand
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.events.ProjectEvent.*
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import java.io.File

class ProjectActionHandler(
    private val projectManager: ProjectManager,
    private val undoRedoManager: UndoRedoManager,
    private val logger: LoggerService,
    private val eventSystem: EventSystem,
) {
    fun init() {
        eventSystem.subscribe<OpenProjectRequested> { event ->
            val command = OpenProjectCommand(projectManager, File(event.projectPath))
            undoRedoManager.executeCommand(command)
            if (command.wasSuccessful()) {
                eventSystem.publish(OpenProjectSucceeded(event.projectPath))
            } else {
                eventSystem.publish(OpenProjectFailed(event.projectPath, "Failed to open project"))
            }
        }
        eventSystem.subscribe<CreateProjectRequested> { event ->
            val command = CreateProjectCommand(projectManager, event.name, event.folderPath, event.engineVersion)
            undoRedoManager.executeCommand(command)
            if (command.wasSuccessful()) {
                eventSystem.publish(CreateProjectSucceeded(event.name, event.folderPath))
            } else {
                eventSystem.publish(
                    CreateProjectFailed(
                        event.name,
                        event.folderPath,
                        command.getFailureReason() ?: "Failed to create project"
                    )
                )
            }
        }
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
        eventSystem.subscribe<CloseProjectRequested> {
            if (projectManager.hasProject()) {
                val command = CloseProjectCommand(projectManager)
                undoRedoManager.executeCommand(command)
            }
        }
        eventSystem.subscribe<SaveProjectRequested> {
            val command = SaveProjectCommand(projectManager)
            undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<LoadLastProjectRequested> {
            val command = LoadLastProjectCommand(projectManager)
            undoRedoManager.executeCommand(command)
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
