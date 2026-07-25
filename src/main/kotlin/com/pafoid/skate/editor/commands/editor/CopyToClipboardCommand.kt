package com.pafoid.skate.editor.commands.editor

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import imgui.ImGui

class CopyToClipboardCommand(private val text: String) : ExecuteOnlyCommand {

    var backup = ""

    override fun execute() {
        backup = ImGui.getClipboardText()
        ImGui.setClipboardText(text)
    }

    override fun undo() {
        ImGui.setClipboardText(backup)
    }

    override fun getDisplayName(): String = "Copy to Clipboard"

    override fun getTargetName(): String = "Clipboard"
}
