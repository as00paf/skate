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

    /** Instanced draw calls and the instances they drew this frame (REN-29 §4.1). */
    val instancedDraws = AtomicInteger(0)
    val instances = AtomicInteger(0)

    /** Terrain streaming (REN-28 §15.5): resident detail chunks, chunks waiting to load, upload time and memory. */
    val terrainResidentChunks = AtomicInteger(0)
    val terrainQueued = AtomicInteger(0)
    val terrainUploadNs = AtomicLong(0)
    val terrainGpuBytes = AtomicLong(0)
    val terrainCpuBytes = AtomicLong(0)
    /** Far-field tiles drawn this frame (REN-28 §15.4). */
    val terrainFarTiles = AtomicInteger(0)

    /** Terrain triangles drawn this frame, all passes (REN-28 §4.1). */
    val terrainTriangles = AtomicInteger(0)

    fun resetDrawCalls() {
        drawCalls.set(0)
        instancedDraws.set(0)
        instances.set(0)
        terrainTriangles.set(0)
        terrainFarTiles.set(0)
    }

    fun resetStateChanges() {
        stateChanges.set(0)
    }
}
