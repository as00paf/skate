package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.animation.FrustumVisibility
import com.pafoid.skate.engine.animation.ik.IkDebugSink
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.ecs.systems.AudioSystem
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.ecs.systems.DayNightCycleSystem
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import com.pafoid.skate.engine.ecs.systems.EnvironmentSystem
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.ecs.systems.PhysicsFrameSystem
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.PlayerMotionSystem
import com.pafoid.skate.engine.ecs.systems.RagdollSystem
import com.pafoid.skate.engine.ecs.systems.ScriptingSystem
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.input.ActionInputReader
import com.pafoid.skate.engine.render.ui.UiFont
import com.pafoid.skate.engine.ui.ReaderUiInputSource
import com.pafoid.skate.engine.ui.UiActions
import com.pafoid.skate.engine.render.ui.SdfFont
import com.pafoid.skate.engine.ui.UiInputSystem
import com.pafoid.skate.engine.ui.UiLayoutSystem
import com.pafoid.skate.engine.ui.UiRenderSystem
import com.pafoid.skate.engine.ui.UiScreenSize
import com.pafoid.skate.engine.ecs.systems.TransformSystem
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.RenderResourcesFactory
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.render.renderer.SkinningUbo
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class Engine {
    private val nativeLibraryLoader = NativeLibraryLoader()

    val serializer = Serializer()
    val logger = LoggerService()
    val jobSystem = JobSystem(logger = logger)
    val eventSystem = EventSystem(logger)

    val assetsManager = AssetsManager(serializer, logger)
    val stringManager = StringManager(logger)
    init { assetsManager.attachAsyncLoading(jobSystem, eventSystem) }
    val audioEngine = AudioEngine(logger)
    val inputProvider = InputProvider(logger)
    val cameraManager = CameraManager(eventSystem)
    val systemManager = SystemManager()
    val sceneManager = SceneManager(assetsManager, eventSystem, serializer, systemManager, logger)
    val gameObjectManager = GameObjectManager()
    val prefabsGenerator = PrefabsGenerator(this)

    val engineState = AtomicReference(EngineState.BOOTING)
    @Volatile
    var runtimePlaying = false
    val screens: MutableList<Screen> = CopyOnWriteArrayList()
    lateinit var renderer: Renderer

    fun start(glfwWindow: Long) {
        MainThread.bind()
        MainThread.violationHandler = { logger.log(it, LogLevel.ERROR) }
        initCallbacks(glfwWindow)

        val resources = RenderResourcesFactory(this@Engine).create(1920, 1080)
        renderer = Renderer(resources, cameraManager)

        engineState.set(EngineState.LOADING)

        nativeLibraryLoader.loadNativeLibrary()
        audioEngine.init()

        engineState.set(EngineState.RUNNING)
        initializeSystems()
        eventSystem.subscribe<EngineAction.SetRuntimePlaying> { event -> runtimePlaying = event.playing }
        logger.log("Engine initialization complete.")
    }

    private fun initCallbacks(glfwWindow: Long) {
        GLFW.glfwSetCursorPosCallback(glfwWindow, inputProvider.mouseListener::mousePosCallback)
        GLFW.glfwSetMouseButtonCallback(glfwWindow, inputProvider.mouseListener::mouseButtonCallback)
        GLFW.glfwSetScrollCallback(glfwWindow, inputProvider.mouseListener::mouseScrollCallback)
        GLFW.glfwSetKeyCallback(glfwWindow, inputProvider.keyListener::keyCallback)
    }

    private fun initializeSystems() {
        val debugRenderer = renderer.renderResources.renderers.debug
        val physicsSystem = PhysicsSystem(debugRenderer, eventSystem)
        val inputSystem = InputSystem(inputProvider, eventSystem)
        val engineSystems = listOf(
            gameObjectManager, // Core
            TransformSystem(),
            cameraManager,
            inputSystem,
            AudioSystem(audioEngine, logger, assetsManager, cameraManager),
            EnvironmentSystem(),
            physicsSystem,
            PhysicsFrameSystem(physicsSystem),
            DayNightCycleSystem(),
            DirectionalLightSystem(cameraManager),
            PlayerMotionSystem(cameraManager),
            AnimationSystem(FrustumVisibility { cameraManager.camera.projection to cameraManager.camera.view }).also {
                it.ikDebugSink = IkDebugSink { from, to, color -> debugRenderer.addLine3D(from, to, color) }
            },
            RagdollSystem(),
            ScriptingSystem(logger, eventSystem),
        ) + uiSystems(inputSystem)

        engineSystems.forEach {
            systemManager.addSystem(it)
        }
        systemManager.start()
    }

    private fun uiSystems(inputSystem: InputSystem): List<System> {
        val framebuffer = renderer.frameBuffer
        val geometry = renderer.renderResources.renderPasses.geometry
        (inputSystem.reader as? ActionInputReader)?.let { UiActions.bindDefaults(it.actions) }
        var font: UiFont? = null
        return listOf(
            UiLayoutSystem(screen = { UiScreenSize(framebuffer.width, framebuffer.height) }),
            UiInputSystem(
                ReaderUiInputSource(inputSystem.reader) { x, y ->
                    val mouse = inputProvider.mouseListener
                    val size = mouse.gameViewportSize
                    (x - mouse.gameViewportPos.x) * framebuffer.width / size.x to
                        (y - mouse.gameViewportPos.y) * framebuffer.height / size.y
                }
            ),
            UiRenderSystem(
                drawList = { geometry.renderer2D.ui },
                strings = stringManager,
                font = { font ?: SdfFont.create().also { font = it } },
                textures = { assetsManager.getTexture(it) },
            ),
        )
    }

    fun update(dt: Float) {
        if (engineState.get() == EngineState.RUNNING) {
            updateRunningState(dt)
        }

        jobSystem.update()
    }

    private fun updateRunningState(dt: Float) {
        if (dt < 0f) return

        sceneManager.currentScene?.let { scene ->
            systemManager.update(dt)
            scene.isRunning = runtimePlaying
            if (runtimePlaying) {
                scene.update(dt)
            }

            systemManager.render()
            renderer.render(scene)
        }
        screens.forEach { it.update(dt) }
        inputProvider.endFrame()
    }

    fun resizeFrameBuffer(width: Int, height: Int) {
        inputProvider.mouseListener.setGameViewportSize(Vector2f(width.toFloat(), height.toFloat()))
        renderer.resize(width, height)
    }

    /**
     * Releases everything that was created, whatever state the engine reached (also after a failed
     * [start]). Each step is isolated so one failure cannot leak the rest; safe to call twice.
     */
    fun destroy() {
        if (engineState.getAndSet(EngineState.DESTROYED) == EngineState.DESTROYED) return

        release("screens") { screens.forEach { it.destroy() }; screens.clear() }
        if (::renderer.isInitialized) {
            release("renderer") { renderer.destroy() }
            release("skinning UBO") { SkinningUbo.destroy() }
        }
        release("scenes") { sceneManager.destroy() }
        release("systems") { systemManager.destroy() }
        release("audio") { audioEngine.close() }
        release("events") { eventSystem.destroy() }
        release("jobs") { jobSystem.destroy() }
        MainThread.unbind()
    }

    private fun release(what: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            logger.logError("Engine.destroy: failed to release $what", e)
        }
    }
}
