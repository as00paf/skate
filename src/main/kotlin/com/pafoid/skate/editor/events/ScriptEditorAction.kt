package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event
import java.io.File

sealed class ScriptEditorAction(eventName: String) : Event(eventName) {
    /** Opens [file] in the Script Editor window. */
    data class Open(val file: File) : ScriptEditorAction("script_editor.open")

    /** Writes [text] to [file] as an undoable command; the runtime hot-reloads the changed file. */
    data class Save(val file: File, val text: String) : ScriptEditorAction("script_editor.save")

    /** Creates a script from the template in [directory] and opens it. */
    data class Create(val directory: File, val input: String) : ScriptEditorAction("script_editor.create")
}
