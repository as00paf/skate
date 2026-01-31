package com.pafoid.skate.engine.editor.logs

data class LogEntry(val message: String, val level: LogLevel, val timestamp: Long = System.currentTimeMillis())