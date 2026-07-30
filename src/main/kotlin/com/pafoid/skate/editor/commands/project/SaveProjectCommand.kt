package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.ProjectManager

class SaveProjectCommand(
    private val projectManager: ProjectManager
) : ExecuteOnlyCommand {

    var backup: Project? = null

    override fun execute() {
        backup = projectManager.currentProject
        projectManager.saveProject()
    }

    override fun undo() {
        backup?.let {
            projectManager.closeProject()
            projectManager.openProject(it)
        }
    }

    override fun getDisplayName(): String = "Save Project"
    override fun getTargetName(): String? = backup?.name
}
