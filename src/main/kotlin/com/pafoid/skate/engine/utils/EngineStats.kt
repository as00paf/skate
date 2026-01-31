package com.pafoid.skate.engine.utils

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object EngineStats {
    val drawCalls = AtomicInteger(0)
    val physicsStepTime = AtomicLong(0) // Nanoseconds
    
    fun resetDrawCalls() {
        drawCalls.set(0)
    }
}
