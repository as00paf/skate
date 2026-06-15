package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.IJobSystem
import org.koin.core.component.KoinComponent
import java.util.concurrent.atomic.AtomicReference

class Engine(
    private val nativeLibraryLoader: NativeLibraryLoader,
    private val audioEngine: AudioEngine,
    private val sceneManager: SceneManager,
    private val renderer: Renderer,
    private val jobSystem: IJobSystem,
    private val systemManager: SystemManager,
    private val logger: LoggerService,
    private val eventSystem: EventSystem,
    private val engineSystems: List<System>,
) : KoinComponent {

    val engineState = AtomicReference(EngineState.BOOTING)
    var runtimePlaying = false

    private var systemManagerStarted = false

    fun start() {
        jobSystem.runOnMain {
            renderer.initialize()
            renderer.useFbo = true

            engineState.set(EngineState.LOADING)

            nativeLibraryLoader.loadNativeLibrary()
            audioEngine.init()

            engineState.set(EngineState.RUNNING)
        }

        initializeSystems()
        eventSystem.subscribe<EngineAction.SetRuntimePlaying> { event -> runtimePlaying = event.playing }
        logger.log("Engine initialization complete.")
    }

    private fun initializeSystems() {
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

        val scene = sceneManager.currentScene
        if (scene != null) {
            if (!systemManagerStarted) {
                systemManager.start()
                systemManagerStarted = true
            }

            systemManager.update(dt)
            scene.isRunning = runtimePlaying
            if (runtimePlaying) {
                scene.update(dt)
            }

            renderer.render(scene, scene.selectedGameObject, scene.hoveredGameObject)
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
