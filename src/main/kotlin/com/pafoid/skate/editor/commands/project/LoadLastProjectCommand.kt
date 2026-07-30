package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.ProjectManager
import java.io.File

class LoadLastProjectCommand(
    private val projectManager: ProjectManager,
    private val settingsManager: EditorSettingsManager,
) : ExecuteOnlyCommand, ExecutionTrackedCommand {
    private var executeSucceeded = false

    override fun execute() {
        settingsManager.recentProjects.firstOrNull()?.let { recent ->
            val projectFile = File(recent.projectPath)
            if (projectFile.exists()) {
                executeSucceeded = projectManager.openProjectFile(projectFile)
            }
        }
    }

    override fun undo() {
        projectManager.closeProject()
    }

    override fun getDisplayName(): String = "Load Last Project"
    override fun getTargetName(): String? = null

    override fun wasSuccessful(): Boolean = executeSucceeded
}
