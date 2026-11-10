package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.commands.CommandNames
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.events.UndoRedoAction
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.flag.ImGuiCol

class CommandHistoryWindow(
    engine: Engine,
    private val undoRedoManager: UndoRedoManager
) : EditorWindow("window.command_history", true) {

    private val stringManager = engine.stringManager
    private val eventSystem = engine.eventSystem

    private var scrollToBottom = false
    private val undoStackHeight = 250f
    private val redoStackHeight = 150f

    override val icon = Icons.HISTORY
    override val relatedWindows = listOf("window.hierarchy", "window.properties")

    override fun imgui() {
        if (!beginWindow(stringManager)) {
            ImGui.end()
            return
        }

        header(stringManager) { renderToolbar() }

        val undoStack = undoRedoManager.getUndoHistory()
        val redoStack = undoRedoManager.getRedoHistory()

        // Empty state
        if (undoStack.isEmpty() && redoStack.isEmpty()) {
            EmptyState.nothingYet(stringManager, Icons.HISTORY, "lbl.command_history.empty", "lbl.command_history.empty_hint")
            ImGui.end()
            return
        }

        renderUndoHistory(undoStack)

        ImGui.separator()
        renderRedoHistory(redoStack)

        ImGui.separator()
        renderFooter()

        ImGui.end()
    }

    private fun renderToolbar() {
        val undoCount = undoRedoManager.getUndoCount()
        val redoCount = undoRedoManager.getRedoCount()

        if (undoCount > 0) {
            if (ImGui.button("${Icons.UNDO} ${stringManager.getString("btn.undo")}")) {
                undoRedoManager.undo()
                scrollToBottom = true
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(stringManager.getString("tooltip.command_history.undo"))
            }
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
            ImGui.button("${Icons.UNDO} ${stringManager.getString("btn.undo")}")
            ImGui.popStyleColor()
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(stringManager.getString("tooltip.command_history.undo_empty"))
            }
        }

        ImGui.sameLine()

        if (redoCount > 0) {
            if (ImGui.button("${Icons.REDO} ${stringManager.getString("btn.redo")}")) {
                undoRedoManager.redo()
                scrollToBottom = true
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(stringManager.getString("tooltip.command_history.redo"))
            }
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
            ImGui.button("${Icons.REDO} ${stringManager.getString("btn.redo")}")
            ImGui.popStyleColor()
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(stringManager.getString("tooltip.command_history.redo_empty"))
            }
        }

        ImGui.sameLine()

        if (undoCount > 0 || redoCount > 0) {
            if (ImGui.button("${Icons.TRASH} ${stringManager.getString("btn.clear_history")}")) {
                eventSystem.publish(UndoRedoAction.ClearHistory)
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(stringManager.getString("tooltip.command_history.clear"))
            }
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
            ImGui.button("${Icons.TRASH} ${stringManager.getString("btn.clear_history")}")
            ImGui.popStyleColor()
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(stringManager.getString("tooltip.command_history.clear_empty"))
            }
        }
    }

    private fun renderUndoHistory(undoStack: List<Command>) {
        // Section header: "Executed Commands (N)"
        ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.9f, 0.6f, 1f)
        ImGui.text("${stringManager.getString("lbl.command_history.section.executed")} (${undoStack.size})")
        ImGui.popStyleColor()

        ImGui.beginChild("UndoHistory", 0f, undoStackHeight)

        // Rendered reversed: most recent command first
        for (i in undoStack.indices.reversed()) {
            val command = undoStack[i]
            val targetName = CommandNames.targetName(command, stringManager) ?: stringManager.getString("lbl.unknown")
            val label = "${Icons.CHECK} ${i + 1}. ${CommandNames.displayName(command, stringManager)} ($targetName)"

            // Executed commands are rendered in white (default) — can be undone
            if (ImGui.selectable(label, false)) {
                // Publish "undo to here" event: undo until stack has `i` entries
                eventSystem.publish(UndoRedoAction.UndoTo(i))
                scrollToBottom = true
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(stringManager.getString("lbl.command_history.shortcuts"))
            }
            stepMenu("##undo_to_$i", "context.command_history.undo_to", Icons.UNDO) {
                eventSystem.publish(UndoRedoAction.UndoTo(i))
            }
        }

        if (scrollToBottom) {
            ImGui.setScrollHereY(1.0f)
            scrollToBottom = false
        }

        ImGui.endChild()
    }

    private fun renderRedoHistory(redoStack: List<Command>) {
        // Section header: "Undone Commands (N)"
        ImGui.pushStyleColor(ImGuiCol.Text, 0.7f, 0.7f, 0.7f, 1f)
        ImGui.text("${stringManager.getString("lbl.command_history.section.undone")} (${redoStack.size})")
        ImGui.popStyleColor()

        ImGui.beginChild("RedoHistory", 0f, redoStackHeight)

        for (i in redoStack.indices) {
            val command = redoStack[i]
            val targetName = CommandNames.targetName(command, stringManager) ?: stringManager.getString("lbl.unknown")
            val label = "${Icons.REDO} ${i + 1}. ${CommandNames.displayName(command, stringManager)} ($targetName)"

            // Undone commands are rendered in gray — visually distinct from executed commands
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
            if (ImGui.selectable(label, false)) {
                // Publish "redo to here" event: redo until stack has `i` entries
                eventSystem.publish(UndoRedoAction.RedoTo(i))
                scrollToBottom = true
            }
            ImGui.popStyleColor()
            stepMenu("##redo_to_$i", "context.command_history.redo_to", Icons.REDO) {
                eventSystem.publish(UndoRedoAction.RedoTo(i))
            }
        }

        ImGui.endChild()
    }

    /** Right-click on a command row: one item that jumps the history to that command (EDT-51 §2.3). */
    private fun stepMenu(id: String, labelKey: String, icon: String, onClick: () -> Unit) {
        if (!ImGui.beginPopupContextItem(id)) return
        if (stringManager.tipped(ImGui.menuItem("$icon ${stringManager.getString(labelKey)}"), labelKey)) {
            onClick()
            scrollToBottom = true
        }
        ImGui.endPopup()
    }

    private fun renderFooter() {
        ImGui.textColored(
            0.5f, 0.5f, 0.5f, 1f,
            stringManager.getString("lbl.command_history.shortcuts")
        )
    }
}
