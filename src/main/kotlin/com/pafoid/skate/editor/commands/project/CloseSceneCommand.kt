package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager

class CloseSceneCommand(
    private val scene: Scene,
    private val sceneManager: SceneManager
) : ExecuteOnlyCommand {

    override fun execute() {
        sceneManager.closeScene(scene)
    }

    override fun undo() {
        sceneManager.openScene(scene)
    }

    override fun getDisplayName(): String = "Close Scene"
    override fun getTargetName(): String = scene.name
}
