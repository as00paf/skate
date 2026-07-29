package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.ProjectManager

class CloseProjectCommand(
    private val projectManager: ProjectManager
) : ExecuteOnlyCommand {

    var backup: Project? = null

    override fun execute() {
        backup = projectManager.currentProject
        projectManager.closeProject()
    }

    override fun undo() {
        backup?.let { projectManager.openProject(it) }
    }

    override fun getDisplayName(): String = "Close Project"
    override fun getTargetName(): String = projectManager.getProjectName()
}
