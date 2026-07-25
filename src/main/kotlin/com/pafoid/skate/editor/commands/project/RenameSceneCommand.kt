package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction

class RenameSceneCommand(
    private val scene: Scene,
    private val newName: String,
    private val oldName: String,
    private val projectManager: ProjectManager,
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem
) : Command {

    override fun execute() {
        if (sceneManager.openScenes.contains(scene)) {
            sceneManager.renameScene(scene, newName, projectManager.currentProject?.projectPath.orEmpty())
        } else {
            scene.name = newName
        }
        eventSystem.publish(SceneAction.Renamed(scene, oldName, newName))
    }

    override fun undo() {
        if (sceneManager.openScenes.contains(scene)) {
            sceneManager.renameScene(scene, oldName, projectManager.currentProject?.projectPath.orEmpty())
        } else {
            scene.name = oldName
        }
        eventSystem.publish(SceneAction.Renamed(scene, newName, oldName))
    }

    override fun getDisplayName(): String = "Rename Scene"
    override fun getTargetName(): String? = newName
}