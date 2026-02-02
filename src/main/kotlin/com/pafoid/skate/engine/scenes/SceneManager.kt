package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.EngineState
import com.pafoid.skate.engine.imgui.ImGuiLayer
import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.editor.CreateGameObjectCommand
import com.pafoid.skate.engine.editor.DeleteGameObjectCommand
import com.pafoid.skate.engine.editor.UndoRedoManager
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.scenes.editor.LevelEditorSceneInitializer
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.engine.utils.serialization.Serializer
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.lwjgl.glfw.GLFW
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.forEachIndexed
import kotlin.getValue

class SceneManager : KoinComponent {

    private val resourceManager: ResourceManager by inject()
    private val keyListener: KeyListener by inject()
    private val logger: LoggerService by inject()
    private val clipboardService: ClipboardService by inject()
    private val serializer: Serializer by inject()
    private val undoRedoManager: UndoRedoManager by inject()

    private var selectedGameObject: GameObject? = null

    fun setSelectedGameObject(gameObject: GameObject?) {
        selectedGameObject = gameObject
    }

    fun getSelectedGameObject(): GameObject? = selectedGameObject

    fun deleteGameObject(gameObject: GameObject) {
        val scene = currentScene ?: return
        undoRedoManager.executeCommand(DeleteGameObjectCommand(gameObject, scene, this))
    }

    fun addGameObject(gameObject: GameObject) {
        val scene = currentScene ?: return
        undoRedoManager.executeCommand(CreateGameObjectCommand(gameObject, scene, this))
    }

    fun undo() {
        undoRedoManager.undo()
    }

    fun redo() {
        undoRedoManager.redo()
    }

    var currentScene: Scene? = null
    var runtimePlaying = false
    val engineState = AtomicReference(EngineState.BOOTING)

    private lateinit var shader3D: Shader
    private lateinit var shader2D: Shader
    private lateinit var shaderPicking: Shader
    private lateinit var shaderPicking3D: Shader
    private lateinit var shaderSkybox: Shader
    private lateinit var shaderSkyDome: Shader
    private lateinit var renderer: Renderer

    private val splashScreenManager = SplashScreenManager()
    private val fadeDuration = 2f

    suspend fun initializeScene(imguiLayer: ImGuiLayer) = withContext(JobSystem.Main) {
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
        Window.show()
        logger.logEngine("Scene initialization complete.")
    }

    private suspend fun initRenderSystem() {
        logger.logEngine("Initializing render system...")
        splashScreenManager.increaseLoadingProgress("Initializing Render System...")
        val shaders = listOf<suspend ()->Unit>(
            { shader3D = resourceManager.loadShader(Assets.Shaders.SHADER_3D_DEFAULT) },
            { shader2D = resourceManager.loadShader(Assets.Shaders.SHADER_2D_BATCH) },
            { shaderPicking = resourceManager.loadShader(Assets.Shaders.PICKING) },
            { shaderPicking3D = resourceManager.loadShader(Assets.Shaders.PICKING_3D) },
            { shaderSkybox = resourceManager.loadShader(Assets.Shaders.SKYBOX) },
            { shaderSkyDome = resourceManager.loadShader(Assets.Shaders.SKY_DOME) },
            { resourceManager.loadShader(Assets.Shaders.DEBUG) },
        )

        shaders.forEachIndexed {
            index, function ->
            logger.logEngine("Loading shader ${index + 1}/${shaders.size}")
            function.invoke()
            splashScreenProgress(index, shaders.size)
        }

        splashScreenManager.increaseLoadingProgress("Initializing Renderer...")

        renderer = Renderer(shader3D, shader2D, shaderPicking, shaderPicking3D, shaderSkybox, shaderSkyDome)
        renderer.useFbo = true
        logger.logEngine("Renderer initialized.")
    }

    private suspend fun splashScreenProgress(index: Int, total: Int) {
        splashScreenManager.increaseLoadingProgress("Loading Shaders $index/$total")
    }

    private suspend fun changeScene(initializer: SceneInitializer, isFirstScene: Boolean = false) {
        if (!isFirstScene) {
            logger.logEditor("Destroying current scene...")
            currentScene?.destroy()
        }
        logger.logEngine("Changing scene to ${initializer::class.simpleName}...")
        val scene = Scene(initializer, serializer)
        currentScene = scene
        // TODO: fix loading of saved scene
        //scene.load()
        scene.init()
        scene.start()
        logger.logEngine("Scene ${initializer::class.simpleName} loaded and started.")
    }

    private fun handleEditorShortcuts(dt: Float, imguiLayer: ImGuiLayer) {
        val ctrlDown = keyListener.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL) || keyListener.isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL)

        if (ctrlDown) {
            // Copy
            if (keyListener.keyBeginPress(GLFW.GLFW_KEY_C)) {
                val selected = getSelectedGameObject()
                if (selected != null) {
                    clipboardService.copy(selected)
                    logger.logEditor("Copied GameObject: ${selected.name}")
                }
            }
            // Cut
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_X)) {
                val selected = getSelectedGameObject()
                if (selected != null) {
                    val cloned = clipboardService.paste() ?: return // paste here returns the copied object from cut
                    clipboardService.copy(selected) // Redundant but following old logic
                    deleteGameObject(selected)
                    logger.logEditor("Cut GameObject: ${selected.name}")
                }
            }
            // Paste
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_V)) {
                val clonedGameObject = clipboardService.paste()
                if (clonedGameObject != null) {
                    // Add to scene at origin for now
                    val origin = Vector3f(0f, 0f, 0f)
                    clonedGameObject.transform.translation.set(origin)
                    
                    // Set parent to null, as it's being pasted as a root object
                    clonedGameObject.parent = null 
                    
                    addGameObject(clonedGameObject)
                    logger.logEditor("Pasted GameObject: ${clonedGameObject.name}")
                }
            }
            // Undo
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_Z)) {
                undo()
                logger.logEditor("Undo")
            }
            // Redo
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_Y)) {
                redo()
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
                scene.update(dt)
            } else {
                scene.editorUpdate(dt)
                handleEditorShortcuts(dt, imguiLayer)
            }

            renderer.render(scene, getSelectedGameObject(), imguiLayer.gameViewWindow.getHoveredObject())
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

        shader3D.destroy()
        shader2D.destroy()
        shaderPicking.destroy()
        shaderPicking3D.destroy()
        shaderSkybox.destroy()
        shaderSkyDome.destroy()
        renderer.destroy()
    }

    fun getPickedId(x: Int, y: Int): Int {
        if (engineState.get() != EngineState.RUNNING) return -1
        return renderer.readPixel(x, y)
    }

    fun getObjectById(id: Int): GameObject? {
        if (engineState.get() != EngineState.RUNNING) return null
        return currentScene?.getGameObject(id)
    }

    fun getJointById(id: Int): com.pafoid.skate.engine.animation.Joint? {
        if (engineState.get() != EngineState.RUNNING) return null
        currentScene?.gameObjects?.forEach { go ->
            go.getComponent<com.pafoid.skate.engine.animation.PoseGizmo>()?.let { pg ->
                val joint = pg.getJointById(id)
                if (joint != null) return joint
            }
        }
        return null
    }

    fun getHoveredObject(x: Int, y: Int): GameObject? {
        if (engineState.get() != EngineState.RUNNING) return null
        val id = renderer.readPixel(x, y)
        return currentScene?.getGameObject(id)
    }

}