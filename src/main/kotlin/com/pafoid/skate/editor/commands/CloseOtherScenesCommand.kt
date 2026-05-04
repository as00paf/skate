package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.ecs.SceneManager

class CloseOtherScenesCommand(
    private val keepIndex: Int,
    private val sceneManager: SceneManager
) : Command {
    override fun execute() {
        sceneManager.closeOtherScenes(keepIndex)
    }

    override fun undo() {
        // Not supported - restoring all closed scenes is complex
    }

    override fun getDisplayName(): String = "Close Other Scenes"
    override fun getTargetName(): String? = keepIndex.toString()
}
