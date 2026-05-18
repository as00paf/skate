package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.systems.ProjectManager

class LoadLastProjectCommand(
    private val projectManager: ProjectManager
) : ExecuteOnlyCommand, ExecutionTrackedCommand {
    private var executeSucceeded = false

    override fun execute() {
        executeSucceeded = projectManager.loadLastProject()
    }

    override fun undo() {
        // Load-last-project is execute-only; previous session context restoration is not reversible.
    }

    override fun getDisplayName(): String = "Load Last Project"
    override fun getTargetName(): String? = null

    override fun wasSuccessful(): Boolean = executeSucceeded
}
