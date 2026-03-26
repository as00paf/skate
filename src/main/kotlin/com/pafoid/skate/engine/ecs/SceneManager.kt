package com.pafoid.skate.engine.ecs

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.ResourceManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SceneManager : KoinComponent {

    private val logger: LoggerService by inject()
    private val resourceManager: ResourceManager by inject()

    val openScenes = mutableListOf<Scene>()
    var activeSceneIndex: Int = -1

    var currentScene: Scene?
        get() = openScenes.getOrNull(activeSceneIndex)
        private set(value) {}

    suspend fun changeScene(scene: Scene, isFirstScene: Boolean = false) {
        if (isFirstScene) {
            openScenes.clear()
            resourceManager.clear()
        }

        // Add the new scene and set it as active
        openScenes.add(scene)
        activeSceneIndex = openScenes.size - 1

        logger.logEngine("Loading scene: ${scene.name}")
        scene.startScene()
        logger.logEngine("Scene ${scene.initializer::class.simpleName} loaded and started.")
    }

    fun switchScene(index: Int) {
        if (index in 0 until openScenes.size) {
            activeSceneIndex = index
            logger.logEditor("Switched to scene: ${currentScene?.name}")
        }
    }

    fun closeScene(index: Int) {
        if (index !in 0 until openScenes.size) return

        val sceneToClose = openScenes[index]
        if (sceneToClose.isDirty) {
            logger.logEditor("Warning: Closing unsaved scene ${sceneToClose.name}")
            // MVP: We just log a warning and close it anyway. A proper implementation would prompt the user.
        }

        logger.logEditor("Destroying scene: ${sceneToClose.name}")
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
}