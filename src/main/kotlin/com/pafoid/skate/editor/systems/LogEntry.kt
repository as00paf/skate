package com.pafoid.skate.editor.systems

data class LogEntry(val message: String, val level: LogLevel, val timestamp: Long = System.currentTimeMillis())