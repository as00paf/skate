package com.pafoid.skate.engine

import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.SplashScreenManager
import com.pafoid.skate.engine.scenes.editor.LevelEditorSceneInitializer
import com.pafoid.skate.engine.utils.JobSystem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

class BootManager(
    private val sceneManager: SceneManager,
    private val renderer: Renderer,
    private val logger: LoggerService,
    private val splashScreenManager: SplashScreenManager,
    private val mainDispatcher: CoroutineDispatcher = JobSystem.Main
) {

    suspend fun boot(engineState: AtomicReference<EngineState>) = withContext(mainDispatcher) {
        logger.logEngine("Initializing Engine...")
        splashScreenManager.init()
        
        engineState.set(EngineState.LOADING)
        
        initRenderSystem()
        val scene = initScene()

        engineState.set(EngineState.RUNNING)
        splashScreenManager.loadingProgress.set(1.0f)
        logger.logEngine("Engine initialization complete.")

        sceneManager.changeScene(scene, true)
    }

    private suspend fun initScene(): Scene {
        val initializer = LevelEditorSceneInitializer()
        initializer.onProgress = { progress, message ->
            splashScreenManager.increaseLoadingProgress(message, progress)
        }
        val scene = Scene("LevelEditorScene", initializer)
        scene.init()
        return scene
    }

    private suspend fun initRenderSystem() {
        logger.logEngine("Initializing render system...")
        splashScreenManager.increaseLoadingProgress("Initializing Render System...", 0f)

        renderer.initFrameBuffer()
        renderer.loadShaders { index, size ->
            splashScreenManager.increaseLoadingProgress("Loading Shaders $index/$size", index / size / 10f)
        }
        renderer.useFbo = true
        logger.logEngine("Renderer initialized.")
    }
}
