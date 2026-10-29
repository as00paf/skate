package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.input.EditBindingsCommand
import com.pafoid.skate.editor.events.KeyBindingsAction
import com.pafoid.skate.editor.input.BindingProfileStore
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputMappings
import java.io.File

class KeyBindingsActionHandler(
    eventSystem: EventSystem,
    private val projectManager: ProjectManager,
    private val undoRedoManager: UndoRedoManager,
    private val actionMap: () -> ActionMap?,
) {
    init {
        eventSystem.subscribe<KeyBindingsAction.Apply> { event ->
            val map = actionMap() ?: return@subscribe
            val mappings = mappings() ?: return@subscribe
            val command = EditBindingsCommand(map, mappings, event.operation)
            if (command.isNoOp()) return@subscribe
            undoRedoManager.executeCommand(command)
            projectManager.saveProject()
        }
        eventSystem.subscribe<KeyBindingsAction.SaveProfile> { event ->
            val map = actionMap() ?: return@subscribe
            store()?.save(map.toProfile(event.name))
        }
        eventSystem.subscribe<KeyBindingsAction.DeleteProfile> { event -> store()?.delete(event.name) }
    }

    private fun mappings(): InputMappings? = projectManager.currentProject?.gameplaySettings?.inputMappings

    private fun store(): BindingProfileStore? =
        projectManager.getProjectDirectory()?.let { BindingProfileStore(File(it, BindingProfileStore.DIRECTORY)) }
}
