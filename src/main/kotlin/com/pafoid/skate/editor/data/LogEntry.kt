package com.pafoid.skate.editor.data

data class LogEntry(val message: String, val level: LogLevel, val timestamp: Long = System.currentTimeMillis())