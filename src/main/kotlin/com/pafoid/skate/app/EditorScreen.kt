package com.pafoid.skate.app

import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.ui.handlers.EditorEventHandler
import com.pafoid.skate.editor.ui.handlers.EditorInputHandler
import com.pafoid.skate.engine.core.Window
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EditorScreen(private val window: Window) : KoinComponent {

    private val settingsManager: SettingsManager by inject()
    private val editorInputHandler: EditorInputHandler by inject()
    private val editorEventHandler: EditorEventHandler by inject()
    private val imGuiLayer: ImGuiLayer by inject()

    var isDestroyed = false

    fun init() {
        editorInputHandler.init(window.glfwWindow)
        editorEventHandler.init()

        imGuiLayer.init(window.windowController)
        settingsManager.load()
    }

    fun update(dt: Float) {
        editorInputHandler.update()
        imGuiLayer.update(dt)
    }

    fun destroy() {
        isDestroyed = true
        imGuiLayer.destroy()
    }
}