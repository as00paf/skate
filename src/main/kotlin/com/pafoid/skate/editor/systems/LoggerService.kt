package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.LogEntry
import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.engine.contracts.EngineLogLevel
import com.pafoid.skate.engine.contracts.EngineLogger
import java.util.concurrent.ConcurrentLinkedQueue

class LoggerService : EngineLogger {

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

    override fun logEngine(message: String, level: EngineLogLevel) {
        logEngine(message, level.toEditorLogLevel())
    }

    override fun logEditor(message: String, level: EngineLogLevel) {
        logEditor(message, level.toEditorLogLevel())
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

    private fun EngineLogLevel.toEditorLogLevel(): LogLevel {
        return when (this) {
            EngineLogLevel.INFO -> LogLevel.INFO
            EngineLogLevel.ACTION -> LogLevel.ACTION
            EngineLogLevel.WARN -> LogLevel.WARN
            EngineLogLevel.ERROR -> LogLevel.ERROR
        }
    }

}
