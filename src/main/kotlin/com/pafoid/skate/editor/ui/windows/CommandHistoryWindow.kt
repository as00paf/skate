package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.events.UndoRedoAction
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.type.ImBoolean

/**
 * Command History window displaying undo/redo stack with visual feedback.
 *
 * This dockable window allows users to:
 * - View all undoable and redoable commands with color-coded distinction
 * - Click on any command entry to jump to that state (via event)
 * - Use Undo/Redo/Clear buttons
 * - See an empty-state message when history is clear
 *
 * Colors:
 * - Executed commands (undo stack): white — available to be undone
 * - Undone commands (redo stack): gray/dimmed — available to be redone
 *
 * All state-changing actions are published as [UndoRedoAction] events
 * and handled by [UndoRedoActionHandler].
 *
 * Usage:
 * - Open from View menu or with Ctrl+Shift+H
 * - Click on any command to undo/redo to that point
 * - Use toolbar buttons for single step undo/redo
 */
class CommandHistoryWindow(
    private val undoRedoManager: UndoRedoManager,
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
) : IWindow {

    private var scrollToBottom = false
    private val undoStackHeight = 250f
    private val redoStackHeight = 150f

    override fun imgui(pOpen: ImBoolean?) {
        ImGui.begin(stringManager.getString("window.command_history"), pOpen)

        renderToolbar()

        ImGui.separator()

        val undoStack = undoRedoManager.getUndoHistory()
        val redoStack = undoRedoManager.getRedoHistory()

        // Empty state
        if (undoStack.isEmpty() && redoStack.isEmpty()) {
            ImGui.spacing()
            ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, stringManager.getString("lbl.command_history.empty"))
            ImGui.textColored(0.4f, 0.4f, 0.4f, 1f, stringManager.getString("lbl.command_history.empty_hint"))
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
            val targetName = command.getTargetName() ?: stringManager.getString("lbl.unknown")
            val label = "${Icons.CHECK} ${i + 1}. ${command.getDisplayName()} ($targetName)"

            // Executed commands are rendered in white (default) — can be undone
            if (ImGui.selectable(label, false)) {
                // Publish "undo to here" event: undo until stack has `i` entries
                eventSystem.publish(UndoRedoAction.UndoTo(i))
                scrollToBottom = true
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("${stringManager.getString("lbl.command_history.shortcuts")}")
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
            val targetName = command.getTargetName() ?: stringManager.getString("lbl.unknown")
            val label = "${Icons.REDO} ${i + 1}. ${command.getDisplayName()} ($targetName)"

            // Undone commands are rendered in gray — visually distinct from executed commands
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
            if (ImGui.selectable(label, false)) {
                // Publish "redo to here" event: redo until stack has `i` entries
                eventSystem.publish(UndoRedoAction.RedoTo(i))
                scrollToBottom = true
            }
            ImGui.popStyleColor()
        }

        ImGui.endChild()
    }

    private fun renderFooter() {
        ImGui.textColored(
            0.5f, 0.5f, 0.5f, 1f,
            stringManager.getString("lbl.command_history.shortcuts")
        )
    }
}
