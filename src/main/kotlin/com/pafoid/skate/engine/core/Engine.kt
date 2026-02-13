package com.pafoid.skate.engine.core

import com.pafoid.skate.app.SplashScreen
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.systems.EditorInputHandler
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.ecs.systems.SceneManager
import com.pafoid.skate.engine.render.renderer.Renderer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicReference

class Engine : KoinComponent {

    private val sceneManager: SceneManager by inject()
    private val renderer: Renderer by inject()
    private val logger: LoggerService by inject()
    private val editorInputHandler: EditorInputHandler by inject()
    private val splashScreen: SplashScreen by inject()

    // Engine State
    val engineState = AtomicReference(EngineState.BOOTING)
    var runtimePlaying = false

    // Physics Loop State
    private var physicsAccumulator = 0f

    companion object {
        private const val FIXED_TIME_STEP = 1.0f / 60.0f
        private const val MAX_TIME_STEP = 0.25f
    }

    fun update(dt: Float, imguiLayer: ImGuiLayer) {
        val state = engineState.get()

        if (state == EngineState.RUNNING) {
            updateRunningState(dt, imguiLayer)
        }

        if (!splashScreen.isDestroyed) {
            splashScreen.update(dt, state)
            splashScreen.render(dt, imguiLayer, state)
        }
    }

    private fun updateRunningState(dt: Float, imguiLayer: ImGuiLayer) {
        val scene = sceneManager.currentScene
        if (dt >= 0 && scene != null) {
            if (runtimePlaying) {
                // Fixed Timestep Loop for Physics
                physicsAccumulator += dt
                if (physicsAccumulator > MAX_TIME_STEP) physicsAccumulator = MAX_TIME_STEP

                while (physicsAccumulator >= FIXED_TIME_STEP) {
                    scene.update(FIXED_TIME_STEP)
                    physicsAccumulator -= FIXED_TIME_STEP
                }
            } else {
                scene.editorUpdate(dt)
                editorInputHandler.update(scene)
            }

            // Render Scene
            renderer.render(scene, scene.getSelectedGameObject(), imguiLayer.gameViewWindow.getHoveredObject())

            // Update ImGui Layer
            imguiLayer.update(dt, scene)
        }
    }

    fun destroy() {
        if (engineState.get() != EngineState.RUNNING) return
        renderer.destroy()
        sceneManager.destroy()
    }
}