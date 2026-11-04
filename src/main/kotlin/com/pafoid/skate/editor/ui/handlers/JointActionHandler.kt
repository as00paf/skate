package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.objects.EditJointCommand
import com.pafoid.skate.editor.events.JointAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem

class JointActionHandler(eventSystem: EventSystem, undoRedoManager: UndoRedoManager) {
    init {
        eventSystem.subscribe<JointAction.Edit> { event ->
            val command = EditJointCommand(event.joint, event.settings)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
    }
}
