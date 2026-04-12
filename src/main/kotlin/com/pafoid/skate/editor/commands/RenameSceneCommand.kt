package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneRenamed
import com.pafoid.skate.engine.ecs.systems.EventSystem

/**
 * Undoable command for renaming a scene.
 * Changes the scene's display name and publishes a [SceneRenamed] event.
 */
class RenameSceneCommand(
    private val scene: Scene,
    private val newName: String,
    private val oldName: String,
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem
) : Command {

    override fun execute() {
        val index = sceneManager.openScenes.indexOf(scene)
        if (index >= 0) {
            sceneManager.renameScene(index, newName)
        } else {
            scene.name = newName
        }
        eventSystem.publish(SceneRenamed(scene, oldName, newName))
    }

    override fun undo() {
        val index = sceneManager.openScenes.indexOf(scene)
        if (index >= 0) {
            sceneManager.renameScene(index, oldName)
        } else {
            scene.name = oldName
        }
        eventSystem.publish(SceneRenamed(scene, newName, oldName))
    }

    override fun getDisplayName(): String = "Rename Scene"
    override fun getTargetName(): String? = newName
}
