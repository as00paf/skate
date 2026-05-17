package com.pafoid.skate.engine.ecs

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.events.SceneAction
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.EventSystem
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SceneManager : KoinComponent {

    private val logger: LoggerService by inject()
    private val resourceManager: ResourceManager by inject()
    private val eventSystem: EventSystem by inject()

    val openScenes = mutableListOf<Scene>()
    var activeSceneIndex: Int = -1

    val currentScene: Scene?
        get() = openScenes.getOrNull(activeSceneIndex)

    suspend fun openScene(scene: Scene, forceSingle: Boolean = false) {
        openSceneBlocking(scene, forceSingle)
    }

    fun openSceneBlocking(scene: Scene, forceSingle: Boolean = false) {
        if (forceSingle) {
            closeAllScenes()
        }

        // Add the new scene and set it as active
        openScenes.add(scene)
        activeSceneIndex = openScenes.size - 1

        logger.logEngine("Loading scene: ${scene.name}")
        scene.start()

        // Publish scene opened event
        eventSystem.publish(SceneAction.Opened(scene))
        eventSystem.publish(SceneAction.Changed)

        logger.logEngine("Scene ${scene.initializer::class.simpleName} loaded and started.")
    }

    fun switchScene(scene: Scene) {
        val sceneIndex = openScenes.indexOf(scene)
        if (sceneIndex < 0) return

        activeSceneIndex = sceneIndex
        logger.logEditor("Switched to scene: ${currentScene?.name}")
        eventSystem.publish(SceneAction.Changed)
    }

    fun switchScene(index: Int) {
        val scene = openScenes.getOrNull(index) ?: return
        switchScene(scene)
    }

    fun closeScene(scene: Scene) {
        val index = openScenes.indexOf(scene)
        if (index < 0) return

        val sceneToClose = openScenes[index]
        if (sceneToClose.isDirty) {
            logger.logEditor("Warning: Closing unsaved scene ${sceneToClose.name}")
            // TODO: Prompt the user for confirmation.
        }

        logger.logEditor("Destroying scene: ${sceneToClose.name}")

        eventSystem.publish(SceneAction.Closing(sceneToClose))
        sceneToClose.destroyScene()
        openScenes.removeAt(index)
        eventSystem.publish(SceneAction.Closed(sceneToClose))

        // Adjust active index
        if (openScenes.isEmpty()) {
            activeSceneIndex = -1
            logger.logEngine("All scenes closed. Clearing resource cache.")
            resourceManager.clear() // Clear resources only when all scenes are closed
        } else if (activeSceneIndex >= index) {
            activeSceneIndex = (activeSceneIndex - 1).coerceAtLeast(0)
        }

        eventSystem.publish(SceneAction.Changed)
    }

    fun closeScene(index: Int) {
        val scene = openScenes.getOrNull(index) ?: return
        closeScene(scene)
    }

    fun destroy() {
        openScenes.forEach { it.destroyScene() }
        openScenes.clear()
        activeSceneIndex = -1
        resourceManager.clear()
    }

    /**
     * Renames a scene.
     * @return true if rename was successful, false if scene is not open
     */
    fun renameScene(scene: Scene, newName: String): Boolean {
        if (!openScenes.contains(scene)) return false
        if (newName.isBlank()) return false

        scene.name = newName
        logger.logEditor("Scene renamed: '${scene.name}'")
        return true
    }

    fun renameScene(index: Int, newName: String): Boolean {
        val scene = openScenes.getOrNull(index) ?: return false
        return renameScene(scene, newName)
    }

    /**
     * Closes all scenes except [keepScene].
     */
    fun closeOtherScenes(keepScene: Scene) {
        if (!openScenes.contains(keepScene)) return

        val scenesToClose = openScenes.filter { it != keepScene }
        scenesToClose.forEach { closeScene(it) }
        switchScene(keepScene)
    }

    fun closeOtherScenes(keepIndex: Int) {
        val keepScene = openScenes.getOrNull(keepIndex) ?: return
        closeOtherScenes(keepScene)
    }

    /**
     * Closes all open scenes.
     */
    fun closeAllScenes() {
        val scenesToClose = openScenes.toList()
        scenesToClose.forEach { closeScene(it) }
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
