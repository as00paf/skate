package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ReportsFailure
import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectRequested
import com.pafoid.skate.editor.systems.ProjectManager
import java.io.File

class CreateProjectCommand(
    private val event: CreateProjectRequested,
    private val projectManager: ProjectManager,
    /** Folder made and filled off the main thread by the loading screen (EDT-56); null does it here. */
    private val preparedDir: File? = null,
) : ExecuteOnlyCommand, ReportsFailure {
    override val errorTitleKey = "error.create_project.title"

    private var executeSucceeded = false
    private var failureReason: String? = null

    override fun execute() {
        val result = projectManager.createProject(event.name, File(event.folderPath), event.graphicsBackend, preparedDir)
        executeSucceeded = result.isSuccess
        failureReason = result.exceptionOrNull()?.message
    }

    override fun undo() {
        // Create operations are not reversible.
    }

    override fun getDisplayName(): String = "Create Project"
    override fun getTargetName(): String = event.name

    override fun wasSuccessful(): Boolean = executeSucceeded

    override fun getFailureReason(): String? = failureReason
}
