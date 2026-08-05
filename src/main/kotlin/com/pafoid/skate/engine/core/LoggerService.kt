package com.pafoid.skate.engine.core

import java.util.concurrent.ConcurrentLinkedQueue

class LoggerService {

    val logs = ConcurrentLinkedQueue<LogEntry>()

    fun log(
        message: String,
        level: LogLevel = LogLevel.INFO,
        source: String = "engine",
        outputToConsole: Boolean = false
    ) {
        logs.add(LogEntry(message, level, source))
        if (logs.size > 2000) logs.poll()
        if (outputToConsole || level == LogLevel.ERROR) println(message)
    }

    fun clearLogs() = logs.clear()

    fun logEngine(message: String, level: LogLevel = LogLevel.INFO) =
        log(message, level, source = "engine", true)

    fun logEditor(message: String, level: LogLevel = LogLevel.INFO) =
        log(message, level, source = "editor", level == LogLevel.ERROR)

    fun logGame(message: String, level: LogLevel = LogLevel.INFO) =
        log(message, level, source = "game", level == LogLevel.ERROR)

    enum class LogLevel {
        INFO, ACTION, WARN, ERROR
    }

    data class LogEntry(
        val message: String,
        val level: LogLevel = LogLevel.INFO,
        val source: String = "engine",
        val timestamp: Long = System.currentTimeMillis()
    )
}
