package com.pafoid.skate.editor.ui.handlers

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
            undoRedoManager.executeCommand(SaveScriptCommand(event.file, event.text))
        }
        eventSystem.subscribe<ScriptEditorAction.Create> { event -> create(event.directory, event.input) }
    }

    private fun create(directory: File, input: String) {
        val name = ScriptTemplates.fileName(input) ?: run {
            logger.logEditor("Invalid script name: $input")
            return
        }
        val file = File(directory, name)
        if (file.exists()) {
            logger.logEditor("Script already exists: ${file.name}")
            return
        }
        undoRedoManager.executeCommand(SaveScriptCommand(file, ScriptTemplates.template()))
        eventSystem.publish(ScriptEditorAction.Open(file))
        eventSystem.publish(WindowAction.Show(WINDOW))
    }

    companion object {
        const val WINDOW = "window.script_editor"
    }
}
