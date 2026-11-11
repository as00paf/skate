package com.pafoid.skate.editor.ui.windows.vcs

import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.vcs.DiffStyle
import com.pafoid.skate.editor.vcs.FileDiff
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiCol

/** EDT-79 §4.4: the diff renderer shared by the Commit and Conflicts dialogs and the Git log window. */
object DiffView {
    /** Draws [diff]; null shows [emptyKey] ("select a file"). Text sides can be labelled ([sides], e.g. mine/theirs). */
    fun render(strings: StringManager, diff: FileDiff?, emptyKey: String = "vcs.diff.none", sides: String? = null) {
        when (diff) {
            null -> EmptyState.nothingSelected(strings, emptyKey)
            is FileDiff.Binary -> {
                ImGui.textUnformatted(diff.path)
                sides?.let { ImGui.textDisabled(it) }
                ImGui.textDisabled(strings.getString("vcs.diff.binary", DiffStyle.size(diff.oldSize), DiffStyle.size(diff.newSize)))
            }
            is FileDiff.Text -> {
                ImGui.textUnformatted(diff.path)
                sides?.let { ImGui.textDisabled(it) }
                ImGui.separator()
                if (diff.lines.isEmpty()) ImGui.textDisabled(strings.getString("vcs.diff.identical"))
                diff.lines.forEach { line ->
                    val style = DiffStyle.of(line.kind)
                    val color = DiffStyle.color(style.role)
                    if (color != null) ImGui.pushStyleColor(ImGuiCol.Text, color[0], color[1], color[2], color[3])
                    ImGui.textUnformatted(style.prefix + line.text)
                    if (color != null) ImGui.popStyleColor()
                }
            }
        }
    }
}
