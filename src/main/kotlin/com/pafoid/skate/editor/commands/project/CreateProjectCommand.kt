package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.events.ProjectEvent.CreateProjectRequested
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.utils.IJobSystem
import java.io.File

class CreateProjectCommand(
    private val event: CreateProjectRequested,
    private val projectManager: ProjectManager,
    private val jobSystem: IJobSystem,
) : ExecuteOnlyCommand, ExecutionTrackedCommand {
    private var executeSucceeded = false
    private var failureReason: String? = null

    override fun execute() {
        //jobSystem.runOnMain {
            val result = projectManager.createProject(event.name, File(event.folderPath), event.engineVersion)
            executeSucceeded = result.isSuccess
            failureReason = result.exceptionOrNull()?.message
        //}
    }

    override fun undo() {
        // Create operations are not reversible.
    }

    override fun getDisplayName(): String = "Create Project"
    override fun getTargetName(): String = event.name

    override fun wasSuccessful(): Boolean = executeSucceeded

    override fun getFailureReason(): String? = failureReason
}
