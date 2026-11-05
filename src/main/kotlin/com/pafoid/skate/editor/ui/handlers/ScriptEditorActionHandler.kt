package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.events.EditorError
import com.pafoid.skate.editor.commands.project.SaveScriptCommand
import com.pafoid.skate.editor.events.ScriptEditorAction
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.scripting.ScriptTemplates
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import java.io.File

class ScriptEditorActionHandler(
    private val eventSystem: EventSystem,
    private val undoRedoManager: UndoRedoManager,
    private val logger: LoggerService,
) {
    init {
        eventSystem.subscribe<ScriptEditorAction.Save> { event ->
            val command = SaveScriptCommand(event.file, event.text)
            undoRedoManager.executeCommand(command)
            if (command.wasSuccessful()) eventSystem.publish(ScriptEditorAction.Saved(event.file, event.text))
        }
        eventSystem.subscribe<ScriptEditorAction.Create> { event -> create(event.directory, event.input) }
    }

    private fun create(directory: File, input: String) {
        val name = ScriptTemplates.fileName(input) ?: run {
            logger.logEditor("Invalid script name: $input")
            eventSystem.publish(EditorError("error.create_script.title", input, "error.create_script.invalid_name"))
            return
        }
        val file = File(directory, name)
        if (file.exists()) {
            logger.logEditor("Script already exists: ${file.name}")
            eventSystem.publish(EditorError("error.create_script.title", file.absolutePath, "error.create_script.exists"))
            return
        }
        val command = SaveScriptCommand(file, ScriptTemplates.template())
        undoRedoManager.executeCommand(command)
        if (!command.wasSuccessful()) return
        eventSystem.publish(ScriptEditorAction.Open(file))
        eventSystem.publish(WindowAction.Show(WINDOW))
    }

    companion object {
        const val WINDOW = "window.script_editor"
    }
}
