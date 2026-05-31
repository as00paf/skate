package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.systems.ProjectManager
import java.io.File

class CreateProjectCommand(
    private val projectManager: ProjectManager,
    private val name: String,
    private val folderPath: String,
    private val engineVersion: String,
) : ExecuteOnlyCommand, ExecutionTrackedCommand {
    private var executeSucceeded = false
    private var failureReason: String? = null

    override fun execute() {
        val result = projectManager.createProject(name, File(folderPath), engineVersion)
        executeSucceeded = result.isSuccess
        failureReason = result.exceptionOrNull()?.message
    }

    override fun undo() {
        // Create operations are not reversible.
    }

    override fun getDisplayName(): String = "Create Project"
    override fun getTargetName(): String = name

    override fun wasSuccessful(): Boolean = executeSucceeded

    override fun getFailureReason(): String? = failureReason
}
