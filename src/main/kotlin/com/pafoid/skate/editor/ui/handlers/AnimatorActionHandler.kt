package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.animation.AnimatorControllerCatalog
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.commands.objects.SetAnimatorControllerCommand
import com.pafoid.skate.editor.commands.objects.SetAnimatorMaskCommand
import com.pafoid.skate.editor.events.AnimatorAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService

class AnimatorActionHandler(eventSystem: EventSystem, undoRedoManager: UndoRedoManager, logger: LoggerService) {
    init {
        val catalog = AnimatorControllerCatalog.shared
        eventSystem.subscribe<ProjectEvent.Opened> { catalog.projectRoot = it.project.getProjectFile().absoluteFile.parentFile }
        eventSystem.subscribe<ProjectEvent.Created> { catalog.projectRoot = it.project.getProjectFile().absoluteFile.parentFile }
        eventSystem.subscribe<ProjectEvent.Closed> { catalog.projectRoot = null }
        eventSystem.subscribe<AnimatorAction.SetController> { event ->
            val command = SetAnimatorControllerCommand(event.animator, event.path, logger)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<AnimatorAction.SetMask> { event ->
            val command = SetAnimatorMaskCommand(event.animator, event.mask)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
    }
}
