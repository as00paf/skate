package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.EditorError
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.editor.imgui.data.Icons
import imgui.ImGui

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
 * and OK. Errors that arrive while one is shown wait their turn. Built on [EditorModal] (EDT-52A).
 */
class ErrorDialog(eventSystem: EventSystem, strings: StringManager) : EditorModal(strings, Icons.INFO) {
    val queue = ErrorQueue()

    init {
        eventSystem.subscribe<EditorError> { queue.push(it) }
    }

    override val titleKey: String get() = queue.current?.titleKey ?: FALLBACK_TITLE

    override val wantsOpen: Boolean get() = queue.current != null

    override fun content() {
        val error = queue.current ?: return
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 40f)
        ImGui.textWrapped(if (error.messageArgs.isEmpty()) strings.getString(error.messageKey) else strings.getString(error.messageKey, *error.messageArgs.toTypedArray()))
        error.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            ImGui.spacing()
            ImGui.textDisabled(detail)
        }
        ImGui.popTextWrapPos()
        if (queue.size > 1) {
            ImGui.spacing()
            ImGui.textDisabled(strings.getString("error.dialog.more", queue.size - 1))
        }
    }

    override fun buttons(): List<FooterButton> {
        val error = queue.current ?: return emptyList()
        val copy = FooterButton("error.dialog.copy", FooterButton.Role.SECONDARY, closes = false) {
            ImGui.setClipboardText("${strings.getString(error.titleKey)}\n${strings.getString(error.messageKey, *error.messageArgs.toTypedArray())}\n${error.detail}")
        }
        val ok = FooterButton("error.dialog.ok", FooterButton.Role.PRIMARY) { queue.dismiss() }
        return if (error.detail.isNullOrBlank()) listOf(ok) else listOf(copy, ok)
    }

    private companion object {
        const val FALLBACK_TITLE = "error.dialog.title"
    }
}
