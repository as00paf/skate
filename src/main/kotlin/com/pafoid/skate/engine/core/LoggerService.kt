package com.pafoid.skate.engine.core

import java.util.concurrent.ConcurrentLinkedQueue

/** Thread-safe log sink; safe to call from jobs and the render loop. */
class LoggerService {

    val logs = ConcurrentLinkedQueue<LogEntry>()
    private val lock = Any()

    /** Console output; replaceable (tests, headless tools). Errors are written to stderr. */
    @Volatile
    var consoleSink: (String, LogLevel) -> Unit = { text, level ->
        val stream = if (level == LogLevel.ERROR) System.err else System.out
        synchronized(lock) { stream.print(text + "\n") }
    }

    fun log(
        message: String,
        level: LogLevel = LogLevel.INFO,
        source: String = "engine",
        outputToConsole: Boolean = false,
        throwable: Throwable? = null
    ) {
        synchronized(lock) {
            logs.add(LogEntry(message, level, source, throwable = throwable))
            while (logs.size > MAX_ENTRIES) logs.poll()
        }
        if (outputToConsole || level == LogLevel.ERROR) {
            consoleSink(if (throwable == null) message else message + "\n" + throwable.stackTraceToString(), level)
        }
    }

    /** Logs [message] at ERROR level together with the exception and its stack trace. */
    fun logError(message: String, throwable: Throwable, source: String = "engine") =
        log("$message: ${throwable.message ?: throwable.javaClass.simpleName}", LogLevel.ERROR, source, throwable = throwable)

    fun clearLogs() = synchronized(lock) { logs.clear() }

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
        val timestamp: Long = System.currentTimeMillis(),
        val throwable: Throwable? = null
    )

    private companion object {
        const val MAX_ENTRIES = 2000
    }
}
