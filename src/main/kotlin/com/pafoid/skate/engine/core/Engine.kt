package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.IJobSystem
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicReference

class Engine : KoinComponent {

    private val bootManager: BootManager by inject()
    private val sceneManager: SceneManager by inject()
    private val renderer: Renderer by inject()
    private val jobSystem: IJobSystem by inject()

    val engineState = AtomicReference(EngineState.BOOTING)
    var runtimePlaying = false

    fun start() {
        jobSystem.runOnMain { bootManager.boot(engineState) }
    }

    fun update(dt: Float) {
        if (engineState.get() == EngineState.RUNNING) {
            updateRunningState(dt)
        } else {
            bootManager.update(dt, engineState)
        }

        jobSystem.update()
    }

    private fun updateRunningState(dt: Float) {
        if (dt < 0f) return

        val scene = sceneManager.currentScene
        if (scene != null) {
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
    }
}
