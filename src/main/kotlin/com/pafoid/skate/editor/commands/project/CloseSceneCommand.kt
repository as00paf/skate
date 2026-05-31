package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager

/**
 * Command for closing a scene.
 * This is execute-only as undoing a close operation is complex
 * (would require reinitializing the scene's physics, systems, and game objects).
 */
class CloseSceneCommand(
    private val scene: Scene,
    private val sceneManager: SceneManager
) : ExecuteOnlyCommand {

    override fun execute() {
        sceneManager.closeScene(scene)
    }

    override fun undo() {
        // Not supported - reinitializing a closed scene is complex
    }

    override fun getDisplayName(): String = "Close Scene"
    override fun getTargetName(): String? = scene.name
}
