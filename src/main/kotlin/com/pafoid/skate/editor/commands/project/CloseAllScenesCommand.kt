package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.ecs.SceneManager

class CloseAllScenesCommand(
    private val sceneManager: SceneManager
) : ExecuteOnlyCommand {
    override fun execute() {
        sceneManager.closeAllScenes()
    }

    override fun undo() {
        // Not supported - recreating and restoring all closed scenes is complex
    }

    override fun getDisplayName(): String = "Close All Scenes"
    override fun getTargetName(): String? = null
}
