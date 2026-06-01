package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.commands.project.CloseProjectCommand
import com.pafoid.skate.editor.commands.project.CreateFileCommand
import com.pafoid.skate.editor.commands.project.CreateProjectCommand
import com.pafoid.skate.editor.commands.project.DeleteFileCommand
import com.pafoid.skate.editor.commands.project.LoadLastProjectCommand
import com.pafoid.skate.editor.commands.project.OpenProjectCommand
import com.pafoid.skate.editor.commands.project.RenameFileCommand
import com.pafoid.skate.editor.commands.project.SaveProjectCommand
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.events.ProjectEvent.CloseProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.CreateFileRequested
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectFailed
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectSucceeded
import com.pafoid.skate.editor.events.ProjectEvent.DeleteFileRequested
import com.pafoid.skate.editor.events.ProjectEvent.LoadLastProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectFailed
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectRequested
import com.pafoid.skate.editor.events.ProjectEvent.OpenProjectSucceeded
import com.pafoid.skate.editor.events.ProjectEvent.RenameFileRequested
import com.pafoid.skate.editor.events.ProjectEvent.SaveProjectRequested
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.utils.IJobSystem
import java.io.File

class ProjectActionHandler(
    private val projectManager: ProjectManager,
    private val undoRedoManager: UndoRedoManager,
    private val logger: LoggerService,
    private val eventSystem: EventSystem,
    private val jobSystem: IJobSystem? = null,
) {
    fun init() {
        eventSystem.subscribe<OpenProjectRequested> { event ->
            executeOnMainThread {
                val command = OpenProjectCommand(projectManager, File(event.projectPath))
                undoRedoManager.executeCommand(command)
                if (command.wasSuccessful()) {
                    eventSystem.publish(OpenProjectSucceeded(event.projectPath))
                } else {
                    eventSystem.publish(OpenProjectFailed(event.projectPath, "Failed to open project"))
                }
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
            executeAndPublishOnSuccess(command, event.path, "create")
        }
        eventSystem.subscribe<RenameFileRequested> { event ->
            val command = RenameFileCommand(event.path, event.newName, logger)
            executeAndPublishOnSuccess(command, event.path, "rename")
        }
        eventSystem.subscribe<DeleteFileRequested> { event ->
            val command = DeleteFileCommand(event.path, logger)
            executeAndPublishOnSuccess(command, event.path, "delete")
        }
        eventSystem.subscribe<CloseProjectRequested> {
            executeOnMainThread {
                if (projectManager.hasProject()) {
                    val command = CloseProjectCommand(projectManager)
                    undoRedoManager.executeCommand(command)
                }
            }
        }
        eventSystem.subscribe<SaveProjectRequested> {
            executeOnMainThread {
                val command = SaveProjectCommand(projectManager)
                undoRedoManager.executeCommand(command)
            }
        }
        eventSystem.subscribe<LoadLastProjectRequested> {
            executeOnMainThread {
                val command = LoadLastProjectCommand(projectManager)
                undoRedoManager.executeCommand(command)
            }
        }
    }

    private fun executeAndPublishOnSuccess(command: ExecutionTrackedCommand, affectedPath: String, operation: String) {
        undoRedoManager.executeCommand(command)
        if (!command.wasSuccessful()) {
            eventSystem.publish(FileSystemEvent.FileSystemOperationFailed(
                path = affectedPath,
                operation = operation,
                reason = command.getFailureReason() ?: "Unknown error"
            ))
            return
        }
        eventSystem.publish(FileSystemEvent.FileSystemChangedEvent(affectedPath))
    }

    private fun executeOnMainThread(block: () -> Unit) {
        val jobs = jobSystem
        if (jobs == null || jobs.isMainThread()) {
            block()
            return
        }
        jobs.runOnMain {
            block()
        }
    }
}
