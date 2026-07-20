package com.pafoid.skate.app

import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.EditorEventHandler
import com.pafoid.skate.editor.ui.handlers.EditorInputHandler
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.Window
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EditorScreen(private val window: Window) : KoinComponent {

    private val settingsManager: SettingsManager by inject()
    private val projectManager: ProjectManager by inject()
    private val editorInputHandler: EditorInputHandler by inject()
    private val imGuiLayer: ImGuiLayer by inject()
    private val eventSystem: EventSystem by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    private val editorEventHandler = EditorEventHandler(eventSystem, imGuiLayer, undoRedoManager)

    fun init() {
        editorInputHandler.init(window.glfwWindow)
        editorEventHandler.init()

        settingsManager.load()
        imGuiLayer.init(window.windowController)
        projectManager.init()
    }

    fun update(dt: Float) {
        editorInputHandler.update()
        imGuiLayer.update(dt)
    }

    fun destroy() {
        imGuiLayer.destroy()
    }
}