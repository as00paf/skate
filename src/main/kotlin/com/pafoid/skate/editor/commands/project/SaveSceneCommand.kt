package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import java.io.File

class SaveSceneCommand(
    private val scene: Scene,
    private val projectManager: ProjectManager,
    private val sceneManager: SceneManager
) : ExecuteOnlyCommand {
    override fun execute() {
        projectManager.currentProject?.let { project ->
            sceneManager.saveScene(scene, File(project.getProjectDirectory(), "Scenes").absolutePath)
        }
    }

    override fun undo() {
        // Save operations are not reversible
    }

    override fun getDisplayName(): String = "Save Scene"
    override fun getTargetName(): String? = scene.name
}
