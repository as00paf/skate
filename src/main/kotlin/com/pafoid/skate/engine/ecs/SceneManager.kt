package com.pafoid.skate.engine.ecs

import com.pafoid.skate.editor.systems.LoggerService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SceneManager : KoinComponent {

    private val logger: LoggerService by inject()

    var currentScene: Scene? = null

    suspend fun changeScene(scene: Scene, isFirstScene: Boolean = false) {
        if (!isFirstScene) {
            logger.logEditor("Destroying current scene...")
            currentScene?.destroyScene()
        }
        logger.logEngine("Changing scene to ${scene.name}...")
        currentScene = scene
        // TODO: fix loading of saved scene
        //scene.load()
        // scene.init() was already called by BootManager or caller
        scene.startScene()
        logger.logEngine("Scene ${scene.initializer::class.simpleName} loaded and started.")
    }

    fun destroy() {
        currentScene?.destroyScene()
    }
}