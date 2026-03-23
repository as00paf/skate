package com.pafoid.skate.engine.ecs

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.ResourceManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SceneManager : KoinComponent {

    private val logger: LoggerService by inject()
    private val resourceManager: ResourceManager by inject()

    var currentScene: Scene? = null

    suspend fun changeScene(scene: Scene, isFirstScene: Boolean = false) {
        if (!isFirstScene) {
            logger.logEditor("Destroying current scene...")
            currentScene?.destroyScene()

            // Clear cached resources to prevent memory leaks during scene transitions
            // Note: Engine-wide resources (shaders, base models) will be reloaded as needed
            logger.logEngine("Clearing resource cache...")
            resourceManager.clear()
        }
        logger.logEngine("Changing scene to ${scene.name}...")
        currentScene = scene
        // scene.init() was already called by BootManager or caller
        scene.startScene()
        logger.logEngine("Scene ${scene.initializer::class.simpleName} loaded and started.")
    }

    fun destroy() {
        currentScene?.destroyScene()
    }
}