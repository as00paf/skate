package com.pafoid.skate.engine

import com.pafoid.skate.engine.editor.EditorInputHandler
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.imgui.ImGuiLayer
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.SplashScreenManager
import com.pafoid.skate.engine.scenes.editor.LevelEditorSceneInitializer
import com.pafoid.skate.engine.scenes.getSelectedGameObject
import com.pafoid.skate.engine.utils.JobSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicReference

class Engine : KoinComponent {

    private val sceneManager: SceneManager by inject()
    private val renderer: Renderer by inject()
    private val logger: LoggerService by inject()
    private val editorInputHandler: EditorInputHandler by inject()
    
    // Engine State
    val engineState = AtomicReference(EngineState.BOOTING)
    var runtimePlaying = false
    
    // Physics Loop State
    private var physicsAccumulator = 0f
    
    // Internal Components
    private val splashScreenManager = SplashScreenManager()
    private val fadeDuration = 2f
    
    companion object {
        private const val FIXED_TIME_STEP = 1.0f / 60.0f
        private const val MAX_TIME_STEP = 0.25f
    }

    suspend fun start() = withContext(JobSystem.Main) {
        logger.logEngine("Initializing Engine...")
        splashScreenManager.init()
        delay(10)
        
        engineState.set(EngineState.LOADING)
        delay(10)
        
        initRenderSystem()
        delay(10)
        
        splashScreenManager.loadingProgress.set(1.0f)
        delay(10)
        
        engineState.set(EngineState.RUNNING)
        
        // Load Initial Scene
        sceneManager.changeScene(LevelEditorSceneInitializer(), true)
        logger.logEngine("Engine initialization complete.")
    }

    private suspend fun initRenderSystem() {
        logger.logEngine("Initializing render system...")
        splashScreenManager.increaseLoadingProgress("Initializing Render System...")

        // Assume renderer initialization handles FBO/Shader loading
        // For now, mirroring SceneManager logic:
        renderer.initFrameBuffer(sceneManager.currentWidth, sceneManager.currentHeight)
        renderer.loadShaders { index, size ->
            splashScreenManager.increaseLoadingProgress("Loading Shaders $index/$size")
        }
        renderer.useFbo = true
        logger.logEngine("Renderer initialized.")
    }

    fun update(dt: Float, imguiLayer: ImGuiLayer) {
        val state = engineState.get()
        
        if (state == EngineState.RUNNING) {
            updateRunningState(dt, imguiLayer)
        }
        
        val isSplashing = state != EngineState.RUNNING || splashScreenManager.splashAlpha > 0f
        if (isSplashing) {
            splash(dt, imguiLayer, state)
        }
    }

    private fun updateRunningState(dt: Float, imguiLayer: ImGuiLayer) {
        val scene = sceneManager.currentScene
        if (dt >= 0 && scene != null) {
            if (runtimePlaying) {
                // Fixed Timestep Loop for Physics
                physicsAccumulator += dt
                if (physicsAccumulator > MAX_TIME_STEP) physicsAccumulator = MAX_TIME_STEP

                while (physicsAccumulator >= FIXED_TIME_STEP) {
                    scene.update(FIXED_TIME_STEP)
                    physicsAccumulator -= FIXED_TIME_STEP
                }
            } else {
                scene.editorUpdate(dt)
                editorInputHandler.update(scene)
            }

            // Render Scene
            renderer.render(scene, scene.getSelectedGameObject(), imguiLayer.gameViewWindow.getHoveredObject())
            
            // Update ImGui Layer
            imguiLayer.update(dt, scene)
        }
    }

    private fun splash(dt: Float, imguiLayer: ImGuiLayer, state: EngineState) {
        val isSplashing = splashScreenManager.splashAlpha > 0f
        val shouldDie = !splashScreenManager.isDestroyed && !isSplashing
        
        if (isSplashing) {
            if (state == EngineState.RUNNING) {
                splashScreenManager.splashAlpha -= dt / fadeDuration
            }

            if (splashScreenManager.splashAlpha < 0f) splashScreenManager.splashAlpha = 0f
            splashScreenManager.render(dt, imguiLayer, engineState.get())
        } else if (shouldDie) {
            splashScreenManager.destroy()
        }
    }

    fun destroy() {
        if (engineState.get() != EngineState.RUNNING) return
        renderer.destroy()
        sceneManager.destroy()
    }
}
