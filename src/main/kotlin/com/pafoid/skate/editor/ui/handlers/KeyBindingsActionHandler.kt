package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.input.EditBindingsCommand
import com.pafoid.skate.editor.commands.input.EditProjectActionsCommand
import com.pafoid.skate.editor.events.KeyBindingsAction
import com.pafoid.skate.editor.input.BindingProfileStore
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.ProjectActions
import com.pafoid.skate.engine.input.RebindResult
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class KeyBindingsActionHandler(
    eventSystem: EventSystem,
    private val projectManager: ProjectManager,
    private val undoRedoManager: UndoRedoManager,
    private val projectActions: () -> ProjectActions? = { null },
    private val actionMap: () -> ActionMap?,
) {
    init {
        eventSystem.subscribe<KeyBindingsAction.Apply> { event ->
            val map = actionMap() ?: return@subscribe
            val mappings = mappings() ?: return@subscribe
            // INP-11: a project action keeps its bindings as defaults in its asset, so the edit goes there.
            val actions = projectActions()
            val baked = actions?.let { EditProjectActionsCommand.bake(map, it.current, mappings.bindings.name, event.operation) }
            if (baked != null) {
                if (baked.result !is RebindResult.Success) return@subscribe
                val op = event.operation
                runProjectEdit(EditProjectActionsCommand(map, mappings, actions, ::writeAsset, baked.set, op.labelKey, op.labelArgs, baked.profile))
                return@subscribe
            }
            val command = EditBindingsCommand(map, mappings, event.operation)
            if (command.isNoOp()) return@subscribe
            undoRedoManager.executeCommand(command)
            projectManager.saveProject()
        }
        eventSystem.subscribe<KeyBindingsAction.EditProjectActions> { event ->
            val map = actionMap() ?: return@subscribe
            val mappings = mappings() ?: return@subscribe
            val actions = projectActions() ?: return@subscribe
            runProjectEdit(EditProjectActionsCommand(map, mappings, actions, ::writeAsset, event.after, event.labelKey, event.labelArgs))
        }
        eventSystem.subscribe<KeyBindingsAction.SaveProfile> { event ->
            val map = actionMap() ?: return@subscribe
            store()?.save(map.toProfile(event.name))
        }
        eventSystem.subscribe<KeyBindingsAction.DeleteProfile> { event -> store()?.delete(event.name) }
    }

    private fun runProjectEdit(command: EditProjectActionsCommand) {
        if (command.isNoOp()) return
        val pathBefore = mappings()?.actionsPath
        undoRedoManager.executeCommand(command)
        // The project file only changes when the asset path is set (first project action).
        if (command.wasSuccessful() && mappings()?.actionsPath != pathBefore) projectManager.saveProject()
    }

    /** Atomic write of a project asset: a temp file next to it, then a rename. Throws on failure. */
    private fun writeAsset(path: String, text: String) {
        val dir = projectManager.getProjectDirectory() ?: error("No project is open")
        val target = File(dir, path)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text)
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun mappings(): InputMappings? = projectManager.currentProject?.gameplaySettings?.inputMappings

    private fun store(): BindingProfileStore? =
        projectManager.getProjectDirectory()?.let { BindingProfileStore(File(it, BindingProfileStore.DIRECTORY)) }
}
