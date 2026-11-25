package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.objects.EditWaterWavesCommand
import com.pafoid.skate.editor.events.WaterAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager

/** Runs the water inspector's edits (EDT-90) as undoable commands. */
class WaterActionHandler(
    eventSystem: EventSystem,
    private val undoRedoManager: UndoRedoManager,
    private val sceneManager: SceneManager? = null,
) {
    init {
        eventSystem.subscribe<WaterAction.EditWaves> { event ->
            val command = EditWaterWavesCommand(event.water, event.before, event.after)
            if (command.isNoOp) return@subscribe
            undoRedoManager.executeCommand(command)
            sceneManager?.currentScene?.isDirty = true
        }
    }
}
