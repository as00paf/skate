package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.systems.ProjectManager

class SaveProjectCommand(
    private val projectManager: ProjectManager
) : ExecuteOnlyCommand {
    override fun execute() {
        projectManager.saveProject()
    }

    override fun undo() {
        // Save operations are not reversible
    }

    override fun getDisplayName(): String = "Save Project"
    override fun getTargetName(): String? = projectManager.getProjectName()
}
