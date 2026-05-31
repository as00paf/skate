package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

sealed class ConsoleAction(eventName: String) : Event(eventName) {
    /** Clears all console log entries from both engine and editor logs. */
    object ClearLogs : ConsoleAction("console.clear_logs")

    /** Copies the given text to the system clipboard. */
    data class CopyToClipboard(val text: String) : ConsoleAction("console.copy_to_clipboard")
}
