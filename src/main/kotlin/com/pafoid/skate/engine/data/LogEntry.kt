package com.pafoid.skate.engine.data

data class LogEntry(
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val source: String = "engine",
    val timestamp: Long = System.currentTimeMillis()
)
