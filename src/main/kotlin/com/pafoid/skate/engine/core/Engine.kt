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
import com.pafoid.skate.engine.ecs.systems.ParticleSystem
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import com.pafoid.skate.engine.ecs.systems.EnvironmentSystem
import com.pafoid.skate.engine.ecs.systems.CameraControlSystem
import com.pafoid.skate.engine.ecs.systems.CameraCycleSystem
import com.pafoid.skate.engine.events.CameraAction
import com.pafoid.skate.engine.gameplay.camera.CameraCycle
import com.pafoid.skate.engine.net.LoopbackPredictionSystem
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.systems.CameraProbe
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ecs.systems.FollowCameraSystem
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.ReaderFlyInputSource
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.ecs.systems.PhysicsFrameSystem
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.MountFollowSystem
import com.pafoid.skate.engine.ecs.systems.PlayerMotionSystem
import com.pafoid.skate.engine.ecs.systems.RagdollSystem
import com.pafoid.skate.engine.ecs.systems.ScriptingSystem
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.input.ActionInputReader
import com.pafoid.skate.engine.scripting.runtime.ReaderScriptInput
import com.pafoid.skate.engine.scripting.runtime.ScriptServices
import com.pafoid.skate.engine.render.ui.UiFont
import com.pafoid.skate.engine.ui.ReaderUiInputSource
import com.pafoid.skate.engine.ui.GameLocale
import com.pafoid.skate.engine.ui.PauseMenuActions
import com.pafoid.skate.engine.ui.PauseMenuSystem
import com.pafoid.skate.engine.ui.UiActions
import com.pafoid.skate.engine.ui.UiMapSystem
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
import com.pafoid.skate.engine.render.rhi.RhiDevice
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
    /** Editor/tool text. Runtime UI never reads it: see [gameStrings] (GUI-10). */
    val stringManager = StringManager(logger)

    /** Runtime UI text (pause menu, HUD, game widgets) with the game's own locale, never the editor's. */
    val gameStrings = StringManager(logger, logSource = "game")
    val gameLocale = GameLocale(gameStrings)
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

    /**
     * Runtime pause (pause menu, GUI-09): the scene stays in Play while simulation, animation and audio are frozen.
     * Only meaningful while [runtimePlaying]; stopping Play clears it.
     */
    val paused: Boolean get() = systemManager.paused

    fun setPaused(value: Boolean) {
        systemManager.paused = value && runtimePlaying
    }

    /** Restart/Quit of the pause menu, set by the host (the exported game or the editor). */
    val pauseActions = PauseMenuActions()

    /** Work queued during the frame's system update, run once the frame is done (see [PauseMenuSystem]). */
    private val afterFrame = ArrayDeque<() -> Unit>()

    /** Set by the editor: UI is being edited, so modal canvases only react while the scene runs. */
    @Volatile
    var uiAuthoring = false
    val screens: MutableList<Screen> = CopyOnWriteArrayList()
    lateinit var renderer: Renderer

    /** The graphics device of the window's context (REN-12 RHI); set by [start]. */
    lateinit var device: RhiDevice
        private set

    fun start(glfwWindow: Long, device: RhiDevice) {
        this.device = device
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
        eventSystem.subscribe<EngineAction.SetRuntimePlaying> { event ->
            runtimePlaying = event.playing
            if (!event.playing) systemManager.paused = false
        }
        logger.log("Engine initialization complete.")
    }

    private fun initCallbacks(glfwWindow: Long) {
        GLFW.glfwSetCursorPosCallback(glfwWindow, inputProvider.mouseListener::mousePosCallback)
        GLFW.glfwSetMouseButtonCallback(glfwWindow, inputProvider.mouseListener::mouseButtonCallback)
        GLFW.glfwSetScrollCallback(glfwWindow, inputProvider.mouseListener::mouseScrollCallback)
        GLFW.glfwSetKeyCallback(glfwWindow, inputProvider.keyListener::keyCallback)
        GLFW.glfwSetCharCallback(glfwWindow, inputProvider.keyListener::charCallback)
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
            AudioSystem(audioEngine, logger, assetsManager, cameraManager, assetsManager.paths::localPath),
            EnvironmentSystem(),
            physicsSystem,
            PhysicsFrameSystem(physicsSystem),
            DayNightCycleSystem(),
            DirectionalLightSystem(cameraManager),
            PlayerMotionSystem(cameraManager),
            MountFollowSystem(),
            FollowCameraSystem(
                CameraProbe { from, to, radius, exclude -> physicsSystem.sphereCast(from, to, radius, exclude.getComponent<RigidBody3D>()) },
                eventSystem,
            ),
            AnimationSystem(FrustumVisibility { cameraManager.camera.projection to cameraManager.camera.view }).also {
                it.eventSystem = eventSystem
                it.ikDebugSink = IkDebugSink { from, to, color -> debugRenderer.addLine3D(from, to, color) }
            },
            RagdollSystem(),
            ParticleSystem(),
            LoopbackPredictionSystem(),
            // Look while a right-button press that started in the game view is held (INP-09 button rule).
            CameraControlSystem(ReaderFlyInputSource(inputSystem.reader)),
            // GPL-22: HUD Camera button / camera.cycle action, and first-person cameras.
            CameraCycleSystem(inputSystem.reader, { cameraManager.camera }) { camera, transform ->
                eventSystem.publish(CameraAction.SetCamera(camera, transform))
            },
            ScriptingSystem(
                logger, eventSystem,
                ScriptServices(input = ReaderScriptInput(inputSystem.reader)),
                sceneManager = sceneManager,
            ),
        ) + uiSystems(inputSystem)

        engineSystems.forEach {
            systemManager.addSystem(it)
        }
        systemManager.start()
    }

    /**
     * The pause menu every host gets: editor Play and the exported game differ only in [pauseActions] (GUI-09, GUI-12).
     * [defer] defaults to the end of the frame; tests run the work at once.
     */
    fun createPauseMenu(defer: (() -> Unit) -> Unit = { afterFrame.addLast(it) }) = PauseMenuSystem(
        setFrozen = ::setPaused,
        actions = pauseActions,
        toggleLanguage = { gameLocale.toggle() },
        volume = { systemManager.getSystem<AudioSystem>()?.masterVolume ?: 1f },
        setVolume = { v -> systemManager.getSystem<AudioSystem>()?.masterVolume = v },
        defer = defer,
    )

    private fun uiSystems(inputSystem: InputSystem): List<System> {
        val framebuffer = renderer.frameBuffer
        val geometry = renderer.renderResources.renderPasses.geometry
        (inputSystem.reader as? ActionInputReader)?.let {
            UiActions.bindDefaults(it.actions)
            CameraCycle.bindDefaults(it.actions)
        }
        var font: UiFont? = null
        val pauseMenu = createPauseMenu()
        return listOf(
            pauseMenu,
            // Map markers are placed before layout (GUI-13).
            UiMapSystem(),
            UiLayoutSystem(screen = { UiScreenSize(framebuffer.width, framebuffer.height) }),
            UiInputSystem(
                // The reader's pointer: render-target pixels and game-view buttons, as for picking (INP-09).
                ReaderUiInputSource(inputSystem.reader)
            ).also {
                it.authoring = { uiAuthoring && !runtimePlaying }
                it.onUnhandledCancel = pauseMenu::open
            },
            UiRenderSystem(
                drawList = { geometry.renderer2D.ui },
                strings = gameStrings,
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
        assetsManager.pollHotReload()
        assetsManager.pollShaderChanges()
    }

    private fun updateRunningState(dt: Float) {
        if (dt < 0f) return

        sceneManager.currentScene?.let { scene ->
            systemManager.update(dt)
            scene.isRunning = runtimePlaying
            if (runtimePlaying && !systemManager.paused) {
                scene.update(dt)
            }

            systemManager.render()
            renderer.render(scene)
        }
        screens.forEach { it.update(dt) }
        inputProvider.endFrame()
        while (afterFrame.isNotEmpty()) afterFrame.removeFirst().invoke()
    }

    fun resizeFrameBuffer(width: Int, height: Int) {
        inputProvider.mouseListener.setTargetSize(width, height)
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
