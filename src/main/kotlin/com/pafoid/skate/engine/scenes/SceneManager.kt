package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.Engine
import com.pafoid.skate.engine.EngineState
import com.pafoid.skate.engine.editor.logs.LoggerService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicReference

class SceneManager : KoinComponent {

    private val logger: LoggerService by inject()
    // Inject Engine lazily to avoid circular dependency in constructor if Engine also injects SceneManager
    private val engine: Engine by inject()

    var currentScene: Scene? = null
    
    // Delegated Properties to maintain compatibility
    var runtimePlaying: Boolean
        get() = engine.runtimePlaying
        set(value) { engine.runtimePlaying = value }

    val engineState: AtomicReference<EngineState>
        get() = engine.engineState

    // Window dimensions (To be moved to Renderer later)
    var currentWidth = 0
    var currentHeight = 0

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
