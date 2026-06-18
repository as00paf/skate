package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.EventSystem

class SaveProjectCommand(
    private val projectManager: ProjectManager,
    private val eventSystem: EventSystem
) : ExecuteOnlyCommand {
    override fun execute() {
        val project = projectManager.currentProject ?: return
        if (projectManager.saveProject()) {
            eventSystem.publish(ProjectEvent.Saved(project))
        }
    }

    override fun undo() {
        // Save operations are not reversible
    }

    override fun getDisplayName(): String = "Save Project"
    override fun getTargetName(): String? = projectManager.getProjectName()
}
