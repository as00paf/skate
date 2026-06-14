package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.editor.ToggleFullScreenCommand
import com.pafoid.skate.editor.events.EditorEvent
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem

class EditorEventHandler(
    private val eventSystem: EventSystem,
    private val imGuiLayer: ImGuiLayer,
    private val undoRedoManager: UndoRedoManager,
) {
    fun init() {
        // Windows
        eventSystem.subscribe<ViewportAction.ToggleFullScreen> {
            undoRedoManager.executeCommand(ToggleFullScreenCommand(imGuiLayer))
        }

        eventSystem.subscribe<EditorEvent.OpenSearch> {
            eventSystem.publish(WindowAction.Show("window.search"))
        }

    }
}