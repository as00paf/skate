package com.pafoid.skate.engine.ecs

import com.pafoid.skate.editor.events.SceneAction
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.contracts.EngineLogLevel
import com.pafoid.skate.engine.contracts.EngineLogger
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.physics3d.Physics3DFactory

class SceneManager(
    private val logger: EngineLogger,
    private val resourceManager: ResourceManager,
    private val eventSystem: EventSystem,
    private val physics3DFactory: Physics3DFactory,
) {

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

        logger.logEngine("Loading scene: ${scene.name}", EngineLogLevel.INFO)
        scene.start()

        // Publish scene opened event
        eventSystem.publish(SceneAction.Opened(scene))
        eventSystem.publish(SceneAction.Changed)

        logger.logEngine("Scene ${scene.name} loaded and started.", EngineLogLevel.INFO)
    }

    fun switchScene(scene: Scene) {
        val sceneIndex = openScenes.indexOf(scene)
        if (sceneIndex < 0) return

        activeSceneIndex = sceneIndex
        logger.logEditor("Switched to scene: ${currentScene?.name}", EngineLogLevel.ACTION)
        eventSystem.publish(SceneAction.Changed)
    }

    fun closeScene(scene: Scene) {
        val index = openScenes.indexOf(scene)
        if (index < 0) return

        val sceneToClose = openScenes[index]
        if (sceneToClose.isDirty) {
            logger.logEditor("Warning: Closing unsaved scene ${sceneToClose.name}", EngineLogLevel.WARN)
            // TODO: Prompt the user for confirmation.
        }

        logger.logEditor("Destroying scene: ${sceneToClose.name}", EngineLogLevel.ACTION)

        eventSystem.publish(SceneAction.Closing(scene))
        sceneToClose.destroyScene()
        openScenes.removeAt(index)
        eventSystem.publish(SceneAction.Closed(scene))

        // Adjust active index
        if (openScenes.isEmpty()) {
            activeSceneIndex = -1
            logger.logEngine("All scenes closed. Clearing resource cache.", EngineLogLevel.INFO)
            resourceManager.clear(preserveNonProjectAssets = true)
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
        logger.logEditor("Scene renamed: '${scene.name}'", EngineLogLevel.ACTION)
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
     * @param filePath Optional file path to back the scene. Sets sceneData.levelPath when provided.
     * @param forceSingle Whether to close all other scenes before opening this scene
     */
    suspend fun createScene(name: String, filePath: String? = null, forceSingle: Boolean = false): Scene? {
        val newScene = Scene(name, physics3DFactory)
        if (filePath != null) {
            newScene.sceneData.levelPath = filePath
        }
        newScene.init()
        openScene(newScene, forceSingle = forceSingle)
        logger.logEditor("Scene created: '$name'", EngineLogLevel.ACTION)
        return newScene
    }
}
