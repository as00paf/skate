package com.pafoid.skate.engine.core

import com.pafoid.skate.editor.EditorWorkspace
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.engine.utils.JobSystem.runOnMain
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicReference

class Engine : KoinComponent {

    private val bootManager: BootManager by inject()
    private val sceneManager: SceneManager by inject()
    private val renderer: Renderer by inject()
    private val imguiLayer: ImGuiLayer by inject()
    private val workspace: EditorWorkspace by inject()

    val engineState = AtomicReference(EngineState.BOOTING)
    var runtimePlaying = false

    fun start() {
        val window = Window(width = 512, height = 512, title = "PAFSK8")
        runOnMain { bootManager.boot(engineState) }
        workspace.init(window.glfwWindow)
        imguiLayer.init(window.windowController)

        window.show(::update)
    }

    fun update(dt: Float) {
        if (engineState.get() == EngineState.RUNNING) {
            updateRunningState(dt)
        } else {
            bootManager.update(dt, imguiLayer, engineState)
        }

        //TODO: should be injected
        JobSystem.update()
    }

    private fun updateRunningState(dt: Float) {
        val scene = sceneManager.currentScene
        if (dt >= 0 && scene != null) {
            workspace.update(dt)
            if (runtimePlaying) {
                scene.update(dt)
                scene.isRunning = true
            } else {
                scene.isRunning = false
            }

            renderer.render(scene, scene.selectedGameObject, scene.hoveredGameObject)
            imguiLayer.update(dt)
        }
    }

    fun destroy() {
        if (engineState.get() != EngineState.RUNNING) return
        imguiLayer.destroy()
        renderer.destroy()
        sceneManager.destroy()
    }
}