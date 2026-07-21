package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.ecs.systems.AudioSystem
import com.pafoid.skate.engine.ecs.systems.DayNightCycleSystem
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import com.pafoid.skate.engine.ecs.systems.EnvironmentSystem
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.GridLines
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.PlayerMotionSystem
import com.pafoid.skate.engine.ecs.systems.RagdollSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.CameraManager
import com.pafoid.skate.engine.render.RenderResourcesFactory
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.JobSystem
import java.util.concurrent.atomic.AtomicReference

class Engine {
    private val nativeLibraryLoader = NativeLibraryLoader()
    val serializer = Serializer()
    val jobSystem = JobSystem()
    val logger = LoggerService()
    val eventSystem = EventSystem()

    val engineState = AtomicReference(EngineState.BOOTING)
    var runtimePlaying = false
    lateinit var renderer: Renderer

    val audioEngine = AudioEngine(logger)
    val inputProvider = InputProvider(logger)
    val cameraManager = CameraManager(eventSystem)
    val assetsManager = AssetsManager(logger)
    val systemManager = SystemManager()
    val sceneManager = SceneManager(assetsManager, eventSystem, serializer, systemManager, logger)
    val gameObjectManager = GameObjectManager()

    private var systemManagerStarted = false

    fun start() {
        jobSystem.runOnMain {
            val renderResourcesFactory = RenderResourcesFactory(assetsManager, cameraManager, logger)
            renderer = Renderer(renderResourcesFactory.create(1920, 1080))
            renderer.useFbo = true

            engineState.set(EngineState.LOADING)

            nativeLibraryLoader.loadNativeLibrary()
            audioEngine.init()

            engineState.set(EngineState.RUNNING)
            initializeSystems()
        }

        eventSystem.subscribe<EngineAction.SetRuntimePlaying> { event -> runtimePlaying = event.playing }
        logger.log("Engine initialization complete.")
    }

    private fun initializeSystems() {
        val debugRenderer = renderer.renderResources.renderers.debug
        val engineSystems = listOf(
            gameObjectManager, // Core
            cameraManager,
            InputSystem(inputProvider, eventSystem),
            AudioSystem(audioEngine, logger, assetsManager),
            EnvironmentSystem(),
            PhysicsSystem(nativeLibraryLoader, debugRenderer),
            DayNightCycleSystem(),
            DirectionalLightSystem(),
            PlayerMotionSystem(cameraManager, eventSystem, logger),
            AnimationSystem(eventSystem, logger),
            RagdollSystem(),
            GridLines(debugRenderer, cameraManager),
        )

        engineSystems.forEach {
            systemManager.addSystem(it)
        }
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
            if (!systemManagerStarted) {
                systemManager.start()
                systemManagerStarted = true
            }

            systemManager.update(dt)
            scene.isRunning = runtimePlaying
            if (runtimePlaying) {
                scene.update(dt)
            }

            renderer.render(scene)
        }
    }

    fun destroy() {
        if (engineState.get() != EngineState.RUNNING) return
        renderer.destroy()
        sceneManager.destroy()
        systemManager.destroy()
        systemManagerStarted = false
    }
}
