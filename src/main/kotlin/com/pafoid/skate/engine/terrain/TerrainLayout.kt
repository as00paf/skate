package com.pafoid.skate.engine.terrain

import kotlin.math.ceil

/**
 * Chunk grid of a terrain (REN-28 §3). Terrain-local coordinates are relative to the terrain's Transform position
 * (rotation ignored: terrain is axis-aligned), with the terrain centred on it: x and z run from `-size / 2` to
 * `size / 2`. Chunk (cx, cz) covers `chunkQuads` quads per side, its corner at [chunkCornerX], [chunkCornerZ]; the
 * terrain's size is rounded up to whole chunks.
 */
data class TerrainLayout(val requestedSize: Float, val spacing: Float, val chunkQuads: Int) {
    init {
        require(requestedSize > 0f && requestedSize.isFinite()) { "Terrain size must be positive, got $requestedSize" }
        require(spacing > 0f && spacing.isFinite()) { "Sample spacing must be positive, got $spacing" }
        require(chunkQuads >= 2 && Integer.bitCount(chunkQuads) == 1) { "chunkQuads must be a power of two >= 2, got $chunkQuads" }
    }

    /** Side of one chunk in metres. */
    val chunkSize: Float = chunkQuads * spacing

    val chunksPerSide: Int = ceil(requestedSize / chunkSize - 1e-4f).toInt().coerceAtLeast(1)

    /** Actual side length: whole chunks. */
    val size: Float = chunksPerSide * chunkSize

    val half: Float get() = size * 0.5f

    /** Samples per chunk side, shared edges included (`chunkQuads + 1`); what a chunk collider uses. */
    val samplesPerChunk: Int get() = chunkQuads + 1

    val chunkCount: Int get() = chunksPerSide * chunksPerSide

    fun chunkCornerX(cx: Int): Float = -half + cx * chunkSize
    fun chunkCornerZ(cz: Int): Float = -half + cz * chunkSize

    /** The chunk holding local ([x], [z]), clamped to the grid. */
    fun chunkX(x: Float): Int = ((x + half) / chunkSize).toInt().coerceIn(0, chunksPerSide - 1)
    fun chunkZ(z: Float): Int = ((z + half) / chunkSize).toInt().coerceIn(0, chunksPerSide - 1)

    fun contains(x: Float, z: Float): Boolean = x >= -half && x <= half && z >= -half && z <= half

    fun isValidChunk(cx: Int, cz: Int): Boolean = cx in 0 until chunksPerSide && cz in 0 until chunksPerSide
}
