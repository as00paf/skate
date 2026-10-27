package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.input.SetDeadZonesCommand
import com.pafoid.skate.editor.events.InputSettingsAction
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem

class InputSettingsActionHandler(
    eventSystem: EventSystem,
    private val projectManager: ProjectManager,
    private val undoRedoManager: UndoRedoManager,
) {
    init {
        eventSystem.subscribe<InputSettingsAction.SetDeadZones> { event ->
            val target = projectManager.currentProject?.gameplaySettings?.inputMappings?.deadZones
                ?: return@subscribe
            val command = SetDeadZonesCommand(target, event.before, event.after, event.label)
            if (command.isNoOp()) return@subscribe
            undoRedoManager.executeCommand(command)
            projectManager.saveProject()
        }
    }
}
