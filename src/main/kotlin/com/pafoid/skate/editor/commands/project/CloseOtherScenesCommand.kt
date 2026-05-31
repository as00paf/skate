package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager

class CloseOtherScenesCommand(
    private val keepScene: Scene?,
    private val sceneManager: SceneManager
) : ExecuteOnlyCommand {

    override fun execute() {
        keepScene?.let { sceneManager.closeOtherScenes(it) }
    }

    override fun undo() {
        // Not supported - restoring all closed scenes is complex
    }

    override fun getDisplayName(): String = "Close Other Scenes"
    override fun getTargetName(): String? = keepScene?.name
}
