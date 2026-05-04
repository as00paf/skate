package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.SceneManager

class SwitchSceneCommand(
    private val index: Int,
    private val sceneManager: SceneManager
) : Command {
    override fun execute() {
        sceneManager.switchScene(index)
    }

    override fun undo() {
        // Not supported - previous selection index is not tracked
    }

    override fun getDisplayName(): String = "Switch Scene"
    override fun getTargetName(): String? = index.toString()
}
