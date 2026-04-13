package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.editor.project.SceneSerializer
import java.io.File

/**
 * Command for deleting a scene and its associated file.
 * This is execute-only as file deletion cannot be safely undone.
 */
class DeleteSceneCommand(
    private val scene: Scene,
    private val index: Int,
    private val sceneManager: SceneManager,
    private val sceneSerializer: SceneSerializer,
    private val logger: LoggerService
) : Command {
    private var wasSaved = false
    private var filePath: String? = null

    override fun execute() {
        filePath = scene.sceneData.levelPath.takeIf { it.isNotEmpty() }
        wasSaved = scene.isDirty

        // If scene has a file, delete it
        filePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
                logger.logEditor("Deleted scene file: $path")
            }
        }

        sceneManager.closeScene(index)
        logger.logEditor("Deleted scene: ${scene.name}")
    }

    override fun undo() {
        // Cannot undo file deletion
        logger.logEditor("Cannot undo scene deletion")
    }

    override fun getDisplayName(): String = "Delete Scene"
    override fun getTargetName(): String? = scene.name
}
