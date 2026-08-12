package com.pafoid.skate.engine.core

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
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.PlayerMotionSystem
import com.pafoid.skate.engine.ecs.systems.RagdollSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.RenderResourcesFactory
import com.pafoid.skate.engine.render.renderer.Renderer
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW
import java.util.concurrent.atomic.AtomicReference

class Engine {
    private val nativeLibraryLoader = NativeLibraryLoader()

    val serializer = Serializer()
    val jobSystem = JobSystem()
    val logger = LoggerService()
    val eventSystem = EventSystem()

    val assetsManager = AssetsManager(serializer, logger)
    val stringManager = StringManager(logger)
    val audioEngine = AudioEngine(logger)
    val inputProvider = InputProvider(logger)
    val cameraManager = CameraManager(eventSystem)
    val systemManager = SystemManager()
    val sceneManager = SceneManager(assetsManager, eventSystem, serializer, systemManager, logger)
    val gameObjectManager = GameObjectManager()
    val prefabsGenerator = PrefabsGenerator(this)

    val engineState = AtomicReference(EngineState.BOOTING)
    var runtimePlaying = false
    val screens: MutableList<Screen> = mutableListOf()
    lateinit var renderer: Renderer

    fun start(glfwWindow: Long) {
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
        val engineSystems = listOf(
            gameObjectManager, // Core
            cameraManager,
            InputSystem(inputProvider, eventSystem),
            AudioSystem(audioEngine, logger, assetsManager, cameraManager),
            EnvironmentSystem(),
            PhysicsSystem(debugRenderer),
            DayNightCycleSystem(),
            DirectionalLightSystem(cameraManager),
            PlayerMotionSystem(cameraManager, eventSystem),
            AnimationSystem(eventSystem, logger),
            RagdollSystem(),
        )

        engineSystems.forEach {
            systemManager.addSystem(it)
        }
        systemManager.start()
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

            renderer.render(scene)
        }
        screens.forEach { it.update(dt) }
        inputProvider.endFrame()
    }

    fun resizeFrameBuffer(width: Int, height: Int) {
        inputProvider.mouseListener.setGameViewportSize(Vector2f(width.toFloat(), height.toFloat()))
        renderer.resize(width, height)
    }

    fun destroy() {
        if (engineState.get() != EngineState.RUNNING) return
        screens.forEach { it.destroy() }
        renderer.destroy()
        sceneManager.destroy()
        systemManager.destroy()
    }
}
