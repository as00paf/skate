package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.EditorError
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags

/** Errors waiting to be shown, oldest first (EDT-10). Capped so a failure loop cannot pile up dialogs. */
class ErrorQueue(private val capacity: Int = 20) {
    private val pending = ArrayDeque<EditorError>()

    val current: EditorError? get() = pending.firstOrNull()
    val size: Int get() = pending.size

    fun push(error: EditorError) {
        if (pending.lastOrNull() == error) return // The same failure twice in a row shows once.
        if (pending.size < capacity) pending.addLast(error)
    }

    fun dismiss() {
        pending.removeFirstOrNull()
    }
}

/**
 * The editor's error dialog (EDT-10): a modal for failed user actions (open, save, export, missing files), fed by
 * [EditorError] events. It shows the title, an explanation, the detail (path or exception text) with a copy button,
 * and OK. Errors that arrive while one is shown wait their turn.
 */
class ErrorDialog(eventSystem: EventSystem, private val strings: StringManager) {
    val queue = ErrorQueue()

    init {
        eventSystem.subscribe<EditorError> { queue.push(it) }
    }

    fun render() {
        val error = queue.current ?: return
        val id = strings.label(error.titleKey, "${com.pafoid.skate.editor.imgui.data.Icons.INFO} ")
        if (!ImGui.isPopupOpen(id)) ImGui.openPopup(id)
        ImGui.setNextWindowSizeConstraints(360f, 0f, 720f, 600f)
        if (!ImGui.beginPopupModal(id, null, ImGuiWindowFlags.AlwaysAutoResize)) return

        ImGui.pushTextWrapPos(ImGui.getFontSize() * 40f)
        ImGui.textWrapped(strings.getString(error.messageKey))
        error.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            ImGui.spacing()
            ImGui.textDisabled(detail)
        }
        ImGui.popTextWrapPos()
        if (queue.size > 1) {
            ImGui.spacing()
            ImGui.textDisabled(strings.getString("error.dialog.more", queue.size - 1))
        }
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        if (strings.tipped(ImGui.button(strings.label("error.dialog.ok"), 120f, 0f), "error.dialog.ok")) {
            queue.dismiss()
            ImGui.closeCurrentPopup()
        }
        ImGui.setItemDefaultFocus()
        if (!error.detail.isNullOrBlank()) {
            ImGui.sameLine()
            if (strings.tipped(ImGui.button(strings.label("error.dialog.copy"), 160f, 0f), "error.dialog.copy")) {
                ImGui.setClipboardText("${strings.getString(error.titleKey)}\n${strings.getString(error.messageKey)}\n${error.detail}")
            }
        }
        ImGui.endPopup()
    }
}
