package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.systems.ProjectManager

class CloseProjectCommand(
    private val projectManager: ProjectManager
) : ExecuteOnlyCommand {
    override fun execute() {
        projectManager.closeProject()
    }

    override fun undo() {
        // Close project is execute-only; restoring full project/session state is not reversible.
    }

    override fun getDisplayName(): String = "Close Project"
    override fun getTargetName(): String? = projectManager.getProjectName()
}
