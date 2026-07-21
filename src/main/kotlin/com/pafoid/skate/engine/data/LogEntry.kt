package com.pafoid.skate.engine.data

data class LogEntry(// TODO: move in logger service
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val source: String = "engine",
    val timestamp: Long = System.currentTimeMillis()
)
