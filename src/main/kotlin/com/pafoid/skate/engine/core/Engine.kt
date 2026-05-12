package com.pafoid.skate.engine.core

import com.pafoid.skate.editor.EditorWorkspace
import com.pafoid.skate.editor.imgui.ImGuiLayer
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
    private val imguiLayer: ImGuiLayer by inject()
    private val workspace: EditorWorkspace by inject()
    private val jobSystem: IJobSystem by inject()

    val engineState = AtomicReference(EngineState.BOOTING)
    var runtimePlaying = false

    fun start() {
        val window = Window(width = 512, height = 512, title = "PAFSK8")
        jobSystem.runOnMain { bootManager.boot(engineState) }
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

        jobSystem.update()
    }

    private fun updateRunningState(dt: Float) {
        val scene = sceneManager.currentScene
        if (dt >= 0 && scene != null) {
            scene.isRunning = runtimePlaying
            workspace.update(dt)
            if (runtimePlaying) {
                scene.update(dt)
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
