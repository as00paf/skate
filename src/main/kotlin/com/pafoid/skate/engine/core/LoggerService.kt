package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.data.LogEntry
import com.pafoid.skate.engine.data.LogLevel
import java.util.concurrent.ConcurrentLinkedQueue

class LoggerService {

    val logs = ConcurrentLinkedQueue<LogEntry>()

    fun log(message: String, level: LogLevel = LogLevel.INFO, source: String = "engine") {
        logs.add(LogEntry(message, level, source))
        if (logs.size > 2000) logs.poll()
        if (level == LogLevel.ERROR) println(message)
    }

    fun clearLogs() = logs.clear()
}
