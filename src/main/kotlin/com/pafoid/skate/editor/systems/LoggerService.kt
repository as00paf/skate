package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.LogEntry
import com.pafoid.skate.editor.data.LogLevel
import java.util.concurrent.ConcurrentLinkedQueue

class LoggerService {

    val engineLogs = ConcurrentLinkedQueue<LogEntry>()
    val editorLogs = ConcurrentLinkedQueue<LogEntry>()

    fun logEngine(message: String, level: LogLevel = LogLevel.INFO) {
        engineLogs.add(LogEntry(message, level))
        if (engineLogs.size > 1000) engineLogs.poll()
    }

    fun logEditor(message: String, level: LogLevel = LogLevel.ACTION) {
        editorLogs.add(LogEntry(message, level))
        if (editorLogs.size > 1000) editorLogs.poll()
    }

    fun clearEngineLogs() {
        engineLogs.clear()
    }

    fun clearEditorLogs() {
        editorLogs.clear()
    }

    fun clearAllLogs() {
        clearEngineLogs()
        clearEditorLogs()
    }

}