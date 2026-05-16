package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import java.io.File

/**
 * Command for deleting a scene and its associated file.
 * This is execute-only as file deletion cannot be safely undone.
 */
class DeleteSceneCommand(
    private val scene: Scene,
    private val sceneManager: SceneManager,
    private val logger: LoggerService
) : ExecuteOnlyCommand {

    private var filePath: String? = null

    override fun execute() {
        filePath = scene.sceneData.levelPath.takeIf { it.isNotEmpty() }

        // If scene has a file, delete it
        filePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
                logger.logEditor("Deleted scene file: $path")
            }
        }

        sceneManager.closeScene(scene)
        logger.logEditor("Deleted scene: ${scene.name}")
    }

    override fun undo() {
        // Cannot undo file deletion
        logger.logEditor("Cannot undo scene deletion")
    }

    override fun getDisplayName(): String = "Delete Scene"
    override fun getTargetName(): String? = scene.name
}
