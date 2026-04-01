package com.pafoid.skate.engine.ecs

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.events.EventSystem
import com.pafoid.skate.engine.events.SceneChanged
import com.pafoid.skate.engine.events.SceneClosed
import com.pafoid.skate.engine.events.SceneOpened
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
            // MVP: We just log a warning and close it anyway. A proper implementation would prompt the user.
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
}