package com.pafoid.skate.engine.ecs

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.ui.handlers.SceneActionHandler
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.events.SceneChanged
import com.pafoid.skate.engine.events.SceneClosed
import com.pafoid.skate.engine.events.SceneOpened
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SceneManager : KoinComponent {

    private val logger: LoggerService by inject()
    private val resourceManager: ResourceManager by inject()
    private val eventSystem: EventSystem by inject()
    private val sceneActionHandler = SceneActionHandler()

    val openScenes = mutableListOf<Scene>()
    var activeSceneIndex: Int = -1

    val currentScene: Scene?
        get() = openScenes.getOrNull(activeSceneIndex)

    init {
        sceneActionHandler.init()
    }

    suspend fun openScene(scene: Scene, forceSingle: Boolean = false) {
        openSceneBlocking(scene, forceSingle)
    }

    fun openSceneBlocking(scene: Scene, forceSingle: Boolean = false) {
        if (forceSingle) {
            openScenes.forEach { it.destroyScene() }
            openScenes.clear()
        }

        // Add the new scene and set it as active
        openScenes.add(scene)
        activeSceneIndex = openScenes.size - 1

        logger.logEngine("Loading scene: ${scene.name}")
        scene.startScene()

        // Publish scene opened event
        eventSystem.publish(SceneOpened(scene))
        eventSystem.publish(SceneChanged)

        logger.logEngine("Scene ${scene.initializer::class.simpleName} loaded and started.")
    }

    fun switchScene(index: Int) {
        if (index in 0 until openScenes.size) {
            activeSceneIndex = index
            logger.logEditor("Switched to scene: ${currentScene?.name}")
            eventSystem.publish(SceneChanged)
        }
    }

    fun closeScene(index: Int) {
        if (index !in 0 until openScenes.size) return

        val sceneToClose = openScenes[index]
        if (sceneToClose.isDirty) {
            logger.logEditor("Warning: Closing unsaved scene ${sceneToClose.name}")
            // TODO: Prompt the user for confirmation.
        }

        logger.logEditor("Destroying scene: ${sceneToClose.name}")
        
        // Publish scene closing event
        eventSystem.publish(SceneClosed(sceneToClose))
        
        sceneToClose.destroyScene()
        openScenes.removeAt(index)

        // Adjust active index
        if (openScenes.isEmpty()) {
            activeSceneIndex = -1
            logger.logEngine("All scenes closed. Clearing resource cache.")
            resourceManager.clear() // Clear resources only when all scenes are closed
        } else if (activeSceneIndex >= index) {
            activeSceneIndex = (activeSceneIndex - 1).coerceAtLeast(0)
        }
    }

    fun destroy() {
        openScenes.forEach { it.destroyScene() }
        openScenes.clear()
        activeSceneIndex = -1
        resourceManager.clear()
    }

    /**
     * Renames a scene at the given index.
     * @return true if rename was successful, false if index is invalid
     */
    fun renameScene(index: Int, newName: String): Boolean {
        val scene = openScenes.getOrNull(index) ?: return false
        if (newName.isBlank()) return false

        scene.name = newName
        logger.logEditor("Scene renamed: '${scene.name}'")
        return true
    }

    /**
     * Closes all scenes except the one at [keepIndex].
     */
    fun closeOtherScenes(keepIndex: Int) {
        if (keepIndex !in 0 until openScenes.size) return

        // Iterate in reverse to avoid index shifting issues
        for (i in openScenes.indices.reversed()) {
            if (i != keepIndex) {
                closeScene(i)
            }
        }
        // After closing others, the kept scene may have shifted to index 0
        if (openScenes.isNotEmpty()) {
            switchScene(0)
        }
    }

    /**
     * Closes all open scenes.
     */
    fun closeAllScenes() {
        // Iterate in reverse to avoid index shifting issues
        for (i in openScenes.indices.reversed()) {
            closeScene(i)
        }
    }

    /**
     * Creates a new scene with the given name and opens it.
     * @param name The name for the new scene
     * @param initializer The scene initializer to use for setup
     * @param filePath Optional file path to back the scene. Sets sceneData.levelPath when provided.
     */
    suspend fun createScene(name: String, initializer: LevelEditorSceneInitializer, filePath: String? = null): Scene? {
        val newScene = Scene(name, initializer)
        if (filePath != null) {
            newScene.sceneData.levelPath = filePath
        }
        newScene.init()
        openScene(newScene)
        logger.logEditor("Scene created: '$name'")
        return newScene
    }
}