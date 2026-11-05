package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ReportsFailure
import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.ProjectManager

class SaveProjectCommand(
    private val projectManager: ProjectManager
) : ExecuteOnlyCommand, ReportsFailure {
    override val errorTitleKey = "error.save_project.title"

    var backup: Project? = null
    private var saved = false

    override fun execute() {
        backup = projectManager.currentProject
        saved = projectManager.saveProject()
    }

    override fun wasSuccessful(): Boolean = saved
    override fun getFailureReason(): String? = projectManager.lastError

    override fun undo() {
        backup?.let {
            projectManager.closeProject()
            projectManager.openProject(it)
        }
    }

    override fun getDisplayName(): String = "Save Project"
    override fun getTargetName(): String? = backup?.name
}
