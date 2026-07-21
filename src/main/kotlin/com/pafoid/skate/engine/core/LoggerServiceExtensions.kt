package com.pafoid.skate.engine.core

import com.pafoid.skate.engine.data.LogLevel

// TODO : move
fun LoggerService.logEngine(message: String, level: LogLevel = LogLevel.INFO) =
    log(message, level, source = "engine")

fun LoggerService.logEditor(message: String, level: LogLevel = LogLevel.INFO) =
    log(message, level, source = "editor")

fun LoggerService.logGame(message: String, level: LogLevel = LogLevel.INFO) =
    log(message, level, source = "game")
