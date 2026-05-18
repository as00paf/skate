package com.pafoid.skate.engine.contracts

interface EngineLogger {
    fun logEngine(message: String, level: EngineLogLevel)
    fun logEditor(message: String, level: EngineLogLevel)
}
