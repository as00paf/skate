package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.project.ProjectManager
import java.io.File

class OpenProjectCommand(
    private val projectManager: ProjectManager,
    private val projectFile: File
) : Command {
    override fun execute() {
        projectManager.openProject(projectFile)
    }

    override fun undo() {
        // Open operations are not reversible
    }

    override fun getDisplayName(): String = "Open Project"
    override fun getTargetName(): String = projectFile.name
}
