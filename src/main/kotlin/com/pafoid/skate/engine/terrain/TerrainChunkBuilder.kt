package com.pafoid.skate.engine.terrain

import java.util.stream.IntStream

/** Builds [TerrainChunkData] from a [TerrainGenerator] (REN-28 §3), alone or all chunks on worker threads. */
object TerrainChunkBuilder {

    /** Chunk ([cx], [cz]) with its apron; only the stamps overlapping it are evaluated. */
    fun build(generator: TerrainGenerator, cx: Int, cz: Int): TerrainChunkData {
        val layout = generator.layout
        val q = layout.chunkQuads
        val spacing = layout.spacing
        val cornerX = layout.chunkCornerX(cx)
        val cornerZ = layout.chunkCornerZ(cz)
        val stride = q + 3
        val heights = FloatArray(stride * stride)
        val stamps = generator.stampIndex.overlapping(cornerX - spacing, cornerZ - spacing, cornerX + (q + 1) * spacing, cornerZ + (q + 1) * spacing)
        for (j in -1..q + 1) {
            val z = cornerZ + j * spacing
            for (i in -1..q + 1) {
                val x = cornerX + i * spacing
                var h = generator.recipeHeight(x, z)
                if (stamps.isNotEmpty()) h = generator.stampIndex.apply(stamps, x, z, h)
                heights[(j + 1) * stride + (i + 1)] = h
            }
        }
        return TerrainChunkData(cx, cz, q, spacing, cornerX, cornerZ, heights)
    }

    /** Every chunk, built in parallel on the common pool when [parallel]; in (cz, cx) order. */
    fun buildAll(generator: TerrainGenerator, parallel: Boolean = true): List<TerrainChunkData> {
        val n = generator.layout.chunksPerSide
        val out = arrayOfNulls<TerrainChunkData>(n * n)
        val range = IntStream.range(0, n * n)
        (if (parallel) range.parallel() else range).forEach { k -> out[k] = build(generator, k % n, k / n) }
        return out.map { it!! }
    }
}
