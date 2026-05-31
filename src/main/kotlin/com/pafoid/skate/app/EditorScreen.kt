package com.pafoid.skate.app

import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.ui.handlers.EditorEventHandler
import com.pafoid.skate.editor.ui.handlers.EditorInputHandler
import com.pafoid.skate.engine.core.Window
import com.pafoid.skate.engine.ecs.systems.GizmoSystem
import com.pafoid.skate.engine.ecs.systems.GridLines
import com.pafoid.skate.engine.ecs.systems.SystemManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EditorScreen(private val window: Window) : KoinComponent {

    private val editorInputHandler: EditorInputHandler by inject()
    private val editorEventHandler: EditorEventHandler by inject()
    private val imGuiLayer: ImGuiLayer by inject()

    private val editorCamera: EditorCamera by inject()
    private val gizmoSystem: GizmoSystem by inject()
    private val gridLines: GridLines by inject()
    private val editorSystems = listOf(editorInputHandler, editorCamera, gizmoSystem, gridLines)

    private val systemManager: SystemManager by inject()

    var isDestroyed = false

    fun init() {
        editorInputHandler.init(window.glfwWindow)
        editorEventHandler.init()

        imGuiLayer.init(window.windowController)
        initEditorSystems()
    }

    private fun initEditorSystems() {
        editorSystems.forEach {
            systemManager.addSystem(it)
        }
    }

    fun update(dt: Float) {
        imGuiLayer.update(dt)
    }

    fun destroy() {
        isDestroyed = true
        imGuiLayer.destroy()
        editorSystems.forEach {
            systemManager.removeSystem(it)
            it.destroy()
        }
        editorInputHandler.destroy()
    }
}