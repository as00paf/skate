package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.editor.project.SceneSerializer
import kotlinx.coroutines.runBlocking

/**
 * Command for creating a new scene and persisting it to disk.
 * This is execute-only as undoing a create operation would require
 * tracking and removing the newly created scene.
 *
 * @param name The name for the new scene
 * @param sceneInitializer The initializer to set up the scene
 * @param sceneManager The scene manager to open the scene with
 * @param sceneSerializer The serializer to persist the scene to disk
 * @param filePath The file path where the scene will be saved
 */
class CreateSceneCommand(
    private val name: String,
    private val sceneInitializer: LevelEditorSceneInitializer,
    private val sceneManager: SceneManager,
    private val sceneSerializer: SceneSerializer,
    private val filePath: String
) : Command {

    /** The created scene instance, available after execute() completes successfully. */
    var createdScene: Scene? = null
        private set

    override fun execute() {
        val newScene = Scene(name, sceneInitializer)
        newScene.sceneData.levelPath = filePath

        // Initialize the scene (loads resources, sets up systems)
        runBlocking {
            newScene.init()
        }

        // Persist to disk
        sceneSerializer.saveToFile(newScene, filePath)

        // Store for post-execution access
        createdScene = newScene
    }

    override fun undo() {
        // Not supported - would require tracking and removing the new scene and deleting the file
    }

    override fun getDisplayName(): String = "Create Scene"
    override fun getTargetName(): String? = name
}
