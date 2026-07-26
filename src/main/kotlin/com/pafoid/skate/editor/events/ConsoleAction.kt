package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

sealed class ConsoleAction(eventName: String) : Event(eventName) {
    object ClearLogs : ConsoleAction("console.clear_logs")
    data class CopyToClipboard(val text: String) : ConsoleAction("console.copy_to_clipboard")
}
