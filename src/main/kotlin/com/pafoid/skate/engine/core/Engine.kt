package com.pafoid.skate.engine.core

import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.JobSystem.runOnMain
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicReference

class Engine : KoinComponent {

    private val bootManager: BootManager by inject()
    private val sceneManager: SceneManager by inject()
    private val renderer: Renderer by inject()
    private val imguiLayer: ImGuiLayer by inject()
    private val editorWorkspace: EditorWorkspace by inject()

    val engineState = AtomicReference(EngineState.BOOTING)
    var runtimePlaying = false

    fun start() {
        val window = Window(width = 512, height = 512, title = "PAFSK8")
        runOnMain { bootManager.boot(engineState) }
        imguiLayer.init(window.windowController)
        window.show()
    }

    fun update(dt: Float) {
        val state = engineState.get()
        if (state == EngineState.RUNNING) {
            updateRunningState(dt)
        } else {
            bootManager.update(dt, imguiLayer, engineState)
        }
    }

    private fun updateRunningState(dt: Float) {
        val scene = sceneManager.currentScene
        if (dt >= 0 && scene != null) {
            if (runtimePlaying) {
                // Update scene with the actual delta time
                scene.updateScene(dt)
            } else {
                // Editor mode: update editor workspace first, then gameplay systems
                editorWorkspace.editorUpdate(dt, scene)
                scene.editorUpdateScene(dt)
            }

            renderer.render(scene, scene.getSelectedGameObject(), imguiLayer.getHoveredGameObject())
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