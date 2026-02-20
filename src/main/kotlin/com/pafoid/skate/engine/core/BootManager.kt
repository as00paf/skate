package com.pafoid.skate.engine.core

import com.pafoid.skate.app.SplashScreen
import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.JobSystem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

class BootManager(
    private val sceneManager: SceneManager,
    private val renderer: Renderer,
    private val logger: LoggerService,
    private val splashScreen: SplashScreen,
    private val mainDispatcher: CoroutineDispatcher = JobSystem.Main
) {

    suspend fun boot(engineState: AtomicReference<EngineState>) = withContext(mainDispatcher) {
        logger.logEngine("Initializing Engine...")
        splashScreen.init()

        engineState.set(EngineState.LOADING)

        // Initialize render resources via the renderer
        initRenderSystem()

        val scene = initScene()

        engineState.set(EngineState.RUNNING)
        splashScreen.loadingProgress.set(1.0f)
        logger.logEngine("Engine initialization complete.")

        sceneManager.changeScene(scene, true)
    }

    private suspend fun initRenderSystem() {
        logger.logEngine("Initializing render system...")
        splashScreen.increaseLoadingProgress("Initializing Render System...", 0f)

        renderer.initialize()
        renderer.useFbo = true

        logger.logEngine("Renderer initialized.")
    }

    private suspend fun initScene(): Scene {
        val initializer = LevelEditorSceneInitializer()
        initializer.onProgress = { progress, message ->
            splashScreen.increaseLoadingProgress(message, progress)
        }
        val scene = Scene("LevelEditorScene", initializer)
        scene.init()
        return scene
    }
}
