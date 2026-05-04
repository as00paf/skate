package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.SceneManager

class CloseAllScenesCommand(
    private val sceneManager: SceneManager
) : Command {
    override fun execute() {
        sceneManager.closeAllScenes()
    }

    override fun undo() {
        // Not supported - recreating and restoring all closed scenes is complex
    }

    override fun getDisplayName(): String = "Close All Scenes"
    override fun getTargetName(): String? = null
}
