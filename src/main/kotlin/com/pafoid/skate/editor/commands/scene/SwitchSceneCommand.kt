package com.pafoid.skate.editor.commands.scene

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager

class SwitchSceneCommand(
    private val scene: Scene?,
    private val sceneManager: SceneManager
) : Command {

    override fun execute() {
        scene?.let { sceneManager.switchScene(it) }
    }

    override fun undo() {
        // Not supported - previous selection index is not tracked
    }

    override fun getDisplayName(): String = "Switch Scene"
    override fun getTargetName(): String? = scene?.name
}