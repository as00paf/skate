package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.editor.SetRenderPassEnabledCommand
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.RenderGraphAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.render.graph.RenderGraph

/**
 * Runs the Render Graph window's pass toggles as commands (EDT-54 §6). Toggles last for the session of a project:
 * every pass runs again when a project opens.
 */
class RenderGraphActionHandler(
    eventSystem: EventSystem,
    private val undoRedoManager: UndoRedoManager,
    private val graph: () -> RenderGraph,
) {
    init {
        eventSystem.subscribe<RenderGraphAction.SetPassEnabled> { event ->
            undoRedoManager.executeCommand(SetRenderPassEnabledCommand(graph(), event.passId, event.enabled, event.stageId))
        }
        eventSystem.subscribe<RenderGraphAction.EnableAll> { enableAll() }
        eventSystem.subscribe<ProjectEvent.Opened> { enableAll() }
    }

    private fun enableAll() {
        val g = graph()
        g.info().passes.filter { !it.enabled && !it.required }.forEach { pass ->
            undoRedoManager.executeCommand(SetRenderPassEnabledCommand(g, pass.id, true))
        }
    }
}
