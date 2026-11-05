package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.ProjectManager

class LoadLastProjectCommand(
    private val projectManager: ProjectManager,
    private val settingsManager: EditorSettingsManager,
) : ExecuteOnlyCommand, ExecutionTrackedCommand {
    private var executeSucceeded = false

    override fun execute() {
        settingsManager.lastProjectFile()?.let { executeSucceeded = projectManager.openProjectFile(it) }
    }

    override fun undo() {
        projectManager.closeProject()
    }

    override fun getDisplayName(): String = "Load Last Project"
    override fun getTargetName(): String? = null

    override fun wasSuccessful(): Boolean = executeSucceeded
}
