package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager

class OpenSceneCommand(
    private val scene: Scene,
    private val sceneManager: SceneManager,
) : Command {

    override fun execute() {
        sceneManager.openScene(scene)
    }

    override fun undo() {
        sceneManager.closeScene(scene)
    }

    override fun getDisplayName(): String = "Open Scene"
    override fun getTargetName(): String? = scene.name
}
