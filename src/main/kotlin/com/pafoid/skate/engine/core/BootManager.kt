package com.pafoid.skate.engine.core

import com.pafoid.skate.app.SplashScreen
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.physics3d.Physics3DFactory
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.Renderer
import java.util.concurrent.atomic.AtomicReference

class BootManager(
    private val sceneManager: SceneManager,
    private val renderer: Renderer,
    private val logger: LoggerService,
    private val splashScreen: SplashScreen,
    private val audioEngine: AudioEngine, //TODO: should be initialized here
    private val resourceManager: ResourceManager,
    private val settingsManager: SettingsManager,
    private val physics3DFactory: Physics3DFactory,
    private val nativeLibraryLoader: NativeLibraryLoader,
) {
    suspend fun boot(engineState: AtomicReference<EngineState>) {
        initRenderer()

        logger.logEngine("Initializing Engine...")
        splashScreen.init()

        engineState.set(EngineState.LOADING)
        settingsManager.load()
        nativeLibraryLoader.loadNativeLibrary()
        preloadEditorResources()

        val scene = initScene()

        engineState.set(EngineState.RUNNING)
        splashScreen.loadingProgress.set(1.0f)
        logger.logEngine("Engine initialization complete.")

        sceneManager.openScene(scene, forceSingle = true)
    }

    fun update(dt: Float, engineState: AtomicReference<EngineState>) {
        if (!splashScreen.isDestroyed) {
            val state = engineState.get()
            splashScreen.update(dt, state)
            splashScreen.render(dt, state)
        }
    }

    private suspend fun initRenderer() {
        logger.logEngine("Initializing render system...")
        splashScreen.increaseLoadingProgress("Initializing Render System...", 0.1f)

        renderer.initialize()
        renderer.useFbo = true

        logger.logEngine("Renderer initialized.")
    }

    private suspend fun initScene(): Scene {
        val scene = Scene("SplashScene", physics3DFactory)
        scene.init()
        return scene
    }

    private suspend fun preloadEditorResources() {
        splashScreen.increaseLoadingProgress("Loading Character Model...", 0.25f)
        resourceManager.loadModel(Assets.Models.JAMES)
        splashScreen.increaseLoadingProgress("Loading Skateboard Model...", 0.5f)
        resourceManager.loadModel(Assets.Models.SKATEBOARD_GLB)
        splashScreen.increaseLoadingProgress("Resources Loaded.", 1f)
    }
}
