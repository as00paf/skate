package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ReportsFailure
import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.ProjectManager

class LoadLastProjectCommand(
    private val projectManager: ProjectManager,
    private val settingsManager: EditorSettingsManager,
) : ExecuteOnlyCommand, ReportsFailure {
    override val errorTitleKey = "error.open_project.title"

    private var executeSucceeded = false

    override fun execute() {
        // No recent project is not an error; failing to open one is.
        executeSucceeded = settingsManager.lastProjectFile()?.let { projectManager.openProjectFile(it) } ?: true
    }

    override fun undo() {
        projectManager.closeProject()
    }

    override fun getDisplayName(): String = "Load Last Project"
    override fun getTargetName(): String? = null

    override fun wasSuccessful(): Boolean = executeSucceeded
    override fun getFailureReason(): String? = projectManager.lastError
}
