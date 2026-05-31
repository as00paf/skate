package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.IJobSystem
import org.koin.core.component.KoinComponent
import java.util.concurrent.atomic.AtomicReference

/**
 * Core runtime engine. Manages the engine lifecycle (boot, update, destroy) and coordinates
 * engine-owned systems via [SystemManager].
 *
 * **Runtime-only:** this class must not receive editor constructs directly. Editor systems
 * (gizmos, grid, picking, ImGui) are registered by [com.pafoid.skate.app.EditorScreen] at the
 * app-composition layer, not injected into this class.
 */
class Engine(
    private val bootManager: BootManager,
    private val sceneManager: SceneManager,
    private val renderer: Renderer,
    private val jobSystem: IJobSystem,
    private val systemManager: SystemManager,
    private val engineSystems: List<System>,
) : KoinComponent {

    val engineState = AtomicReference(EngineState.BOOTING)
    var runtimePlaying = false

    private var systemManagerStarted = false

    fun start() {
        jobSystem.runOnMain {
            renderer.initialize()
            renderer.useFbo = true

            bootManager.boot(engineState)
        }

        initializeSystems()
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
