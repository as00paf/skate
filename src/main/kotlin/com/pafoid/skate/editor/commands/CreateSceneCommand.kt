package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.SceneCreated
import com.pafoid.skate.engine.utils.IJobSystem

/**
 * Command for creating a new scene and persisting it to disk.
 * This is execute-only as undoing a create operation would require
 * tracking and removing the newly created scene.
 *
 * @param name The name for the new scene
 * @param sceneInitializer The initializer to set up the scene
 * @param sceneSerializer The serializer to persist the scene to disk
 * @param filePath The file path where the scene will be saved
 */
class CreateSceneCommand(
    private val name: String,
    private val sceneInitializer: LevelEditorSceneInitializer,
    private val sceneSerializer: SceneSerializer,
    private val filePath: String,
    private val jobSystem: IJobSystem,
    private val eventSystem: EventSystem,
    private val sceneFactory: (String, LevelEditorSceneInitializer) -> Scene = { sceneName, initializer ->
        Scene(sceneName, initializer)
    }
) : Command {

    /** The created scene instance, available after the scheduled create job completes successfully. */
    var createdScene: Scene? = null
        private set

    override fun execute() {
        jobSystem.runOnMain {
            val newScene = sceneFactory(name, sceneInitializer)
            newScene.sceneData.levelPath = filePath
            newScene.init()

            sceneSerializer.saveToFile(newScene, filePath)

            createdScene = newScene
            eventSystem.publish(SceneCreated(newScene))
        }
    }

    override fun undo() {
        // Not supported - would require tracking and removing the new scene and deleting the file
    }

    override fun getDisplayName(): String = "Create Scene"
    override fun getTargetName(): String? = name
}
