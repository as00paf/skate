package com.pafoid.skate.engine.editor.logs

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

}