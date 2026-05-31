package com.pafoid.skate.editor.commands.editor

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import imgui.ImGui

/**
 * Copies the given text to the system clipboard via ImGui.
 * This is an execute-only command — clipboard operations are not undoable.
 */
class CopyToClipboardCommand(private val text: String) : ExecuteOnlyCommand {

    override fun execute() {
        ImGui.setClipboardText(text)
    }

    override fun undo() {
        // Execute-only: clipboard copy cannot be undone
    }

    override fun getDisplayName(): String = "Copy to Clipboard"

    override fun getTargetName(): String? = null
}
