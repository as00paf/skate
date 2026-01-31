package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.EngineState
import com.pafoid.skate.engine.imgui.ImGuiLayer
import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.scenes.editor.LevelEditorSceneInitializer
import com.pafoid.skate.engine.utils.JobSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.forEachIndexed
import kotlin.getValue

class SceneManager : KoinComponent {

    private val resourceManager: ResourceManager by inject()

    companion object {
        fun get(): SceneManager = (object : KoinComponent {}).get()

        fun getCurrentScene(): Scene? = get().currentScene
        fun isPlaying(): Boolean = get().runtimePlaying
        fun setPlaying(playing: Boolean) { get().runtimePlaying = playing }
    }

    private var currentScene: Scene? = null
    private var runtimePlaying = false
    private val engineState = AtomicReference(EngineState.BOOTING)

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
    }

    private suspend fun initRenderSystem() {
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

        shaders.forEachIndexed { index, function ->
            function.invoke()
            splashScreenProgress(index, shaders.size)
        }

        splashScreenManager.increaseLoadingProgress("Initializing Renderer...")

        renderer = Renderer(shader3D, shader2D, shaderPicking, shaderPicking3D, shaderSkybox, shaderSkyDome)
        renderer.useFbo = true
    }

    private suspend fun splashScreenProgress(index: Int, total: Int) {
        splashScreenManager.increaseLoadingProgress("Loading Shaders $index/$total")
    }

    private suspend fun changeScene(initializer: SceneInitializer, isFirstScene: Boolean = false) {
        if (!isFirstScene) currentScene?.destroy()
        val scene = Scene(initializer)
        currentScene = scene
        // TODO: fix loading of saved scene
        //scene.load()
        scene.init()
        scene.start()
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
            }

            renderer.render(scene, imguiLayer.propertiesWindow.getActiveObject(), imguiLayer.gameViewWindow.getHoveredObject())
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

    fun getPickedObject(x: Int, y: Int): GameObject? {
        if (engineState.get() != EngineState.RUNNING) return null
        val id = renderer.readPixel(x, y)
        return currentScene?.getGameObject(id)
    }

}