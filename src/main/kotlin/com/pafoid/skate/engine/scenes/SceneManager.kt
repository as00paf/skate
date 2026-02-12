package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.EngineState
import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.editor.CreateGameObjectCommand
import com.pafoid.skate.engine.editor.DeleteGameObjectCommand
import com.pafoid.skate.engine.editor.UndoRedoManager
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.imgui.ImGuiLayer
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.scenes.editor.LevelEditorSceneInitializer
import com.pafoid.skate.engine.utils.JobSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW
import java.util.concurrent.atomic.AtomicReference

class SceneManager : KoinComponent {

    private val renderer: Renderer by inject()
    private val keyListener: KeyListener by inject()
    private val logger: LoggerService by inject()
    private val clipboardService: ClipboardService by inject()
    private val undoRedoManager: UndoRedoManager by inject()

    var currentScene: Scene? = null
    var runtimePlaying = false
    val engineState = AtomicReference(EngineState.BOOTING)

    private val splashScreenManager = SplashScreenManager()
    private val fadeDuration = 2f

    private var physicsAccumulator = 0f

    var currentWidth = 0
    var currentHeight = 0

    suspend fun initializeScene() = withContext(JobSystem.Main) {
        logger.logEngine("Initializing scene...")
        splashScreenManager.init()
        delay(10)
        engineState.set(EngineState.LOADING)
        delay(10)
        initRenderSystem()
        delay(10)
        splashScreenManager.loadingProgress.set(1.0f)
        delay(10)
        engineState.set(EngineState.RUNNING)

        changeScene(LevelEditorSceneInitializer(), true)
        logger.logEngine("Scene initialization complete.")
    }

    private suspend fun initRenderSystem() {
        logger.logEngine("Initializing render system...")
        splashScreenManager.increaseLoadingProgress("Initializing Render System...")

        renderer.initFrameBuffer(currentWidth, currentHeight)
        renderer.loadShaders { index, size ->
            splashScreenManager.increaseLoadingProgress("Loading Shaders $index/$size")
        }
        renderer.useFbo = true
        logger.logEngine("Renderer initialized.")
    }

    private suspend fun changeScene(initializer: SceneInitializer, isFirstScene: Boolean = false) {
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

    private fun handleEditorShortcuts() {
        val ctrlDown = keyListener.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL) || keyListener.isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL)

        if (ctrlDown) {
            // Copy
            if (keyListener.keyBeginPress(GLFW.GLFW_KEY_C)) {
                val selected = currentScene?.getSelectedGameObject()
                if (selected != null) {
                    clipboardService.copy(selected)
                    logger.logEditor("Copied GameObject: ${selected.name}")
                }
            }
            // Cut
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_X)) {
                val scene = currentScene
                val selected = scene?.getSelectedGameObject()
                if (selected != null) {
                    val cloned = clipboardService.paste() ?: return // paste here returns the copied object from cut
                    clipboardService.copy(selected) // Redundant but following old logic
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(selected, scene))
                    logger.logEditor("Cut GameObject: ${selected.name}")
                }
            }
            // Paste
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_V)) {
                val scene = currentScene
                val clonedGameObject = clipboardService.paste()
                if (clonedGameObject != null) {
                    // Add to scene at origin for now
                    val origin = Vector3f(0f, 0f, 0f)
                    clonedGameObject.getComponent<Transform>()?.translation?.set(origin)
                    
                    // Set parent to null, as it's being pasted as a root object
                    clonedGameObject.parent = null

                    scene?.let { undoRedoManager.executeCommand(CreateGameObjectCommand(clonedGameObject, it)) }
                    logger.logEditor("Pasted GameObject: ${clonedGameObject.name}")
                }
            }
            // Undo
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_Z)) {
                undoRedoManager.undo()
                logger.logEditor("Undo")
            }
            // Redo
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_Y)) {
                undoRedoManager.redo()
                logger.logEditor("Redo")
            }
        }
    }

    fun draw(dt: Float, imguiLayer: ImGuiLayer) {
        val state = engineState.get()

        if(state == EngineState.RUNNING) drawScene(dt, imguiLayer)
        val isSplashing = state != EngineState.RUNNING || splashScreenManager.splashAlpha > 0f

        if(isSplashing) {
            splash(dt, imguiLayer, state)
        }
    }

    private fun drawScene(dt: Float, imguiLayer: ImGuiLayer) {
        val scene = currentScene
        if (dt >= 0 && scene != null) {
            if (runtimePlaying) {
                // Fixed Timestep Loop
                physicsAccumulator += dt
                if (physicsAccumulator > MAX_TIME_STEP) physicsAccumulator = MAX_TIME_STEP

                while (physicsAccumulator >= FIXED_TIME_STEP) {
                    scene.update(FIXED_TIME_STEP)
                    physicsAccumulator -= FIXED_TIME_STEP
                }
            } else {
                scene.editorUpdate(dt)
                handleEditorShortcuts()
            }

            renderer.render(scene, currentScene?.getSelectedGameObject(), imguiLayer.gameViewWindow.getHoveredObject())
            imguiLayer.update(dt, scene)
        }
    }

    private fun splash(dt: Float, imguiLayer: ImGuiLayer, state: EngineState) {
        val isSplashing = splashScreenManager.splashAlpha > 0f
        val shouldDie = !splashScreenManager.isDestroyed && !isSplashing
        if(isSplashing) {
            if(state == EngineState.RUNNING){
                splashScreenManager.splashAlpha -= dt / fadeDuration
            }

            if (splashScreenManager.splashAlpha < 0f) splashScreenManager.splashAlpha = 0f
            splashScreenManager.render(dt, imguiLayer, engineState.get())
        } else if(shouldDie) {
            splashScreenManager.destroy()
        }
    }

    fun destroy() {
        if (engineState.get() != EngineState.RUNNING) return
        renderer.destroy()
    }

    companion object {
        private const val FIXED_TIME_STEP = 1.0f / 60.0f
        private const val MAX_TIME_STEP = 0.25f
    }

}