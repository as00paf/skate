package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.project.ProjectManager

class SaveProjectCommand(
    private val projectManager: ProjectManager
) : Command {
    override fun execute() {
        projectManager.saveProject()
    }

    override fun undo() {
        // Save operations are not reversible
    }

    override fun getDisplayName(): String = "Save Project"
    override fun getTargetName(): String? = projectManager.getProjectName()
}
