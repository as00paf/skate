package com.pafoid.skate.engine.terrain

import java.util.stream.IntStream
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The whole terrain at a coarse spacing (REN-28 §3.2, §15.4): always resident, it feeds the far field and gameplay
 * height queries away from the loaded chunks. 8 m spacing gives 2049² samples for 16 km. Heights in metres,
 * terrain-local, row-major by Z from the terrain's (-half, -half) corner.
 */
class TerrainOverview(val layout: TerrainLayout, val spacing: Float, val heights: FloatArray) {
    val samplesPerSide: Int = ceil(layout.size / spacing).toInt() + 1

    init {
        require(heights.size == samplesPerSide * samplesPerSide) { "Expected ${samplesPerSide * samplesPerSide} heights, got ${heights.size}" }
    }

    fun sample(i: Int, j: Int): Float =
        heights[j.coerceIn(0, samplesPerSide - 1) * samplesPerSide + i.coerceIn(0, samplesPerSide - 1)]

    /** Bilinear height at local ([x], [z]), clamped to the terrain. */
    fun heightAt(x: Float, z: Float): Float {
        val fx = ((x + layout.half) / spacing).coerceIn(0f, (samplesPerSide - 1).toFloat())
        val fz = ((z + layout.half) / spacing).coerceIn(0f, (samplesPerSide - 1).toFloat())
        val i = floor(fx).toInt().coerceAtMost(samplesPerSide - 2).coerceAtLeast(0)
        val j = floor(fz).toInt().coerceAtMost(samplesPerSide - 2).coerceAtLeast(0)
        val u = fx - i; val v = fz - j
        val a = sample(i, j) + (sample(i + 1, j) - sample(i, j)) * u
        val b = sample(i, j + 1) + (sample(i + 1, j + 1) - sample(i, j + 1)) * u
        return a + (b - a) * v
    }

    /** Re-samples the overview over [chunk]'s area from its heights (an edited chunk; REN-28 §15.7 incremental refresh). */
    fun refresh(chunk: TerrainChunkData) {
        val size = chunk.chunkQuads * chunk.spacing
        val i0 = ceil((chunk.cornerX + layout.half) / spacing).toInt(); val i1 = floor((chunk.cornerX + size + layout.half) / spacing).toInt()
        val j0 = ceil((chunk.cornerZ + layout.half) / spacing).toInt(); val j1 = floor((chunk.cornerZ + size + layout.half) / spacing).toInt()
        for (j in j0.coerceAtLeast(0)..j1.coerceAtMost(samplesPerSide - 1)) for (i in i0.coerceAtLeast(0)..i1.coerceAtMost(samplesPerSide - 1)) {
            val ci = ((i * spacing - layout.half - chunk.cornerX) / chunk.spacing).toInt().coerceIn(0, chunk.chunkQuads)
            val cj = ((j * spacing - layout.half - chunk.cornerZ) / chunk.spacing).toInt().coerceIn(0, chunk.chunkQuads)
            heights[j * samplesPerSide + i] = chunk.height(ci, cj)
        }
    }

    companion object {
        const val DEFAULT_SPACING = 8f

        /** Generates the overview from [generator] (recipe and stamps), rows in parallel when [parallel]. */
        fun build(generator: TerrainGenerator, spacing: Float = maxOf(DEFAULT_SPACING, generator.layout.spacing), parallel: Boolean = true): TerrainOverview {
            val layout = generator.layout
            val n = ceil(layout.size / spacing).toInt() + 1
            val heights = FloatArray(n * n)
            val rows = IntStream.range(0, n)
            (if (parallel) rows.parallel() else rows).forEach { j ->
                val z = -layout.half + j * spacing
                for (i in 0 until n) heights[j * n + i] = generator.heightAt(-layout.half + i * spacing, z)
            }
            return TerrainOverview(layout, spacing, heights)
        }
    }
}
