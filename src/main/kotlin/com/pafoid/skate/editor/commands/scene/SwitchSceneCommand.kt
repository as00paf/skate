package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager

class SwitchSceneCommand(
    private val scene: Scene?,
    private val sceneManager: SceneManager
) : ExecuteOnlyCommand {

    var backup: Scene? = null

    override fun execute() {
        backup = sceneManager.currentScene
        scene?.let { sceneManager.switchScene(it) }
    }

    override fun undo() {
        backup?.let { sceneManager.switchScene(it) }
    }

    override fun getDisplayName(): String = "Switch Scene"
    override fun getTargetName(): String? = scene?.name
}
