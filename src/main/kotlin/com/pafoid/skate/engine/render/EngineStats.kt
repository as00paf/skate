package com.pafoid.skate.engine.render

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object EngineStats {
    val drawCalls = AtomicInteger(0)

    /** GL state changes actually applied this frame (toggles, VAO and texture binds); redundant ones are skipped. */
    val stateChanges = AtomicInteger(0)

    /** Skinned characters whose animation was evaluated / skipped (off-screen throttle) in the last update. */
    val animationsUpdated = AtomicInteger(0)
    val animationsSkipped = AtomicInteger(0)

    /** Bone palette UBO uploads performed / avoided (palette unchanged since the last upload) this frame. */
    val skinUploads = AtomicInteger(0)
    val skinUploadsSkipped = AtomicInteger(0)
    val physicsStepTime = AtomicLong(0) // Nanoseconds

    fun resetDrawCalls() {
        drawCalls.set(0)
    }

    fun resetStateChanges() {
        stateChanges.set(0)
    }
}
