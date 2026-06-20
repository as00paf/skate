package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.systems.ProjectManager
import java.io.File

class OpenProjectCommand(
    private val projectManager: ProjectManager,
    private val projectFile: File
) : ExecuteOnlyCommand, ExecutionTrackedCommand {
    private var executeSucceeded = false

    override fun execute() {
        executeSucceeded = projectManager.openProject(projectFile)
    }

    override fun undo() {
        projectManager.closeProject()
    }

    override fun getDisplayName(): String = "Open Project"
    override fun getTargetName(): String = projectFile.name

    override fun wasSuccessful(): Boolean = executeSucceeded
}
