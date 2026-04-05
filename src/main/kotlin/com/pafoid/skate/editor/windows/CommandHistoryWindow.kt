package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Command History window displaying undo/redo stack with visual feedback.
 *
 * This dockable window allows users to:
 * - View all undoable and redoable commands
 * - Click on any command to jump to that state
 * - Use Undo/Redo/Clear buttons
 * - See command names and target objects
 *
 * Usage:
 * - Open from View menu or with Ctrl+Shift+H
 * - Click on any command to undo/redo to that point
 * - Use toolbar buttons for single step undo/redo
 */
class CommandHistoryWindow : IWindow, KoinComponent {

    private val undoRedoManager: UndoRedoManager by inject()
    private val stringManager: StringManager by inject()

    private var scrollToBottom = false
    private val undoStackHeight = 250f
    private val redoStackHeight = 150f

    override fun imgui(pOpen: ImBoolean?) {
        ImGui.begin(stringManager.getString("window.command_history"), pOpen)

        renderToolbar()

        ImGui.separator()

        renderUndoHistory()

        ImGui.separator()
        renderRedoHistory()

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
                undoRedoManager.clear()
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

    private fun renderUndoHistory() {
        val undoStack = undoRedoManager.getUndoHistory()

        ImGui.text("${stringManager.getString("lbl.command_history.undo")} (${undoStack.size})")

        ImGui.beginChild("UndoHistory", 0f, undoStackHeight)

        for (i in undoStack.indices.reversed()) {
            val command = undoStack[i]
            val targetName = command.getTargetName() ?: stringManager.getString("lbl.unknown")
            val label = "${i + 1}. ${command.getDisplayName()} ($targetName)"

            if (ImGui.selectable(label, false)) {
                undoTo(i)
                scrollToBottom = true
            }
        }

        if (scrollToBottom) {
            ImGui.setScrollHereY(1.0f)
            scrollToBottom = false
        }

        ImGui.endChild()
    }

    private fun renderRedoHistory() {
        val redoStack = undoRedoManager.getRedoHistory()

        ImGui.text("${stringManager.getString("lbl.command_history.redo")} (${redoStack.size})")

        ImGui.beginChild("RedoHistory", 0f, redoStackHeight)

        for (i in redoStack.indices) {
            val command = redoStack[i]
            val targetName = command.getTargetName() ?: stringManager.getString("lbl.unknown")
            val label = "${i + 1}. ${command.getDisplayName()} ($targetName)"

            if (ImGui.selectable(label, false)) {
                redoTo(i)
                scrollToBottom = true
            }
        }

        ImGui.endChild()
    }

    private fun renderFooter() {
        ImGui.textColored(0.5f, 0.5f, 0.5f, 1f,
            "${stringManager.getString("lbl.command_history.shortcuts")}")
    }

    private fun undoTo(index: Int) {
        while (undoRedoManager.getUndoCount() > index) {
            undoRedoManager.undo()
        }
    }

    private fun redoTo(index: Int) {
        while (undoRedoManager.getRedoCount() > index) {
            undoRedoManager.redo()
        }
    }
}
