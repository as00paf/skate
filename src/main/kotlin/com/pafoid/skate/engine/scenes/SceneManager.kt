package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.editor.logs.LoggerService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SceneManager : KoinComponent {

    private val logger: LoggerService by inject()

    var currentScene: Scene? = null

    suspend fun changeScene(initializer: SceneInitializer, isFirstScene: Boolean = false) {
        if (!isFirstScene) {
            logger.logEditor("Destroying current scene...")
            currentScene?.destroy()
        }
        logger.logEngine("Changing scene to ${initializer::class.simpleName}...")
        val scene = Scene(initializer)
        currentScene = scene
        // TODO: fix loading of saved scene
        //scene.load()
        scene.init()
        scene.start()
        logger.logEngine("Scene ${initializer::class.simpleName} loaded and started.")
    }

    fun destroy() {
        currentScene?.destroy()
    }
}
