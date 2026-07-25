package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager

class DeleteSceneCommand(
    private val scene: Scene,
    private val projectManager: ProjectManager,
    private val sceneManager: SceneManager,
    private val logger: LoggerService
) : ExecuteOnlyCommand {

    override fun execute() {
        projectManager.currentProject?.let { project ->
            sceneManager.deleteScene(project.projectPath, scene)
        }

        logger.logEditor("Deleted scene: ${scene.name}")
    }

    override fun undo() {
        projectManager.currentProject?.let { project ->
            sceneManager.saveScene(scene, project.projectPath)
        }
    }

    override fun getDisplayName(): String = "Delete Scene"
    override fun getTargetName(): String? = scene.name
}
