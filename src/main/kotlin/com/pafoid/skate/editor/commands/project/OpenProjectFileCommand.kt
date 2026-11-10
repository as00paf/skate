package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ReportsFailure
import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.systems.PreparedProject
import com.pafoid.skate.editor.systems.ProjectManager
import java.io.File

class OpenProjectFileCommand(
    private val projectManager: ProjectManager,
    private val projectFile: File,
    /** Read off the main thread by the loading screen (EDT-56); null reads it here. */
    private val prepared: PreparedProject? = null,
) : ExecuteOnlyCommand, ReportsFailure {
    override val errorTitleKey = "error.open_project.title"

    private var executeSucceeded = false

    override fun execute() {
        executeSucceeded = projectManager.openProjectFile(projectFile, prepared)
    }

    override fun undo() {
        projectManager.closeProject()
    }

    override fun getDisplayName(): String = "Open Project File"
    override fun getTargetName(): String = projectFile.name

    override fun wasSuccessful(): Boolean = executeSucceeded
    override fun getFailureReason(): String? = projectManager.lastError
}
