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
        return TerrainChunkData(cx, cz, q, spacing, cornerX, cornerZ, heights).also { refreshDerived(generator, it, stamps) }
    }

    /**
     * Recomputes [chunk]'s splat weights, dominant biome ids and scatter from its current heights (after building, or
     * after a sculpt edit). No biome setup: weights stay null and the shader shades by height and slope.
     */
    fun refreshDerived(generator: TerrainGenerator, chunk: TerrainChunkData, stamps: List<TerrainStamp>? = null) {
        val biomes = generator.biomes
        val map = generator.biomeMap
        if (biomes.isEmpty() || map == null) { chunk.splat = null; chunk.biomeIds = null; chunk.scatter = emptyList(); return }
        val q = chunk.chunkQuads
        val n = q + 1
        val splat = ByteArray(n * n * TerrainWeights.LAYERS)
        val ids = ByteArray(n * n)
        val biomeWeights = FloatArray(biomes.size)
        val layerWeights = FloatArray(TerrainWeights.LAYERS)
        val normal = org.joml.Vector3f()
        val painted = chunk.paintedBiomeIds?.takeIf { generator.biomeSource == BiomeSource.PAINTED && it.size == n * n }
        for (j in 0..q) for (i in 0..q) {
            val x = chunk.cornerX + i * chunk.spacing; val z = chunk.cornerZ + j * chunk.spacing
            val h = chunk.height(i, j)
            if (painted != null) paintedWeights(painted, n, i, j, biomeWeights) else map.weightsAt(x, z, h, biomeWeights)
            chunk.normal(i, j, normal)
            val slope = Math.toDegrees(kotlin.math.acos(normal.y.coerceIn(-1f, 1f).toDouble())).toFloat()
            TerrainWeights.layerWeights(biomes, biomeWeights, h, slope, layerWeights)
            TerrainWeights.pack(layerWeights, splat, (j * n + i) * TerrainWeights.LAYERS)
            var best = 0
            for (b in biomeWeights.indices) if (biomeWeights[b] > biomeWeights[best]) best = b
            ids[j * n + i] = best.toByte()
        }
        chunk.splat = splat
        chunk.biomeIds = ids
        val reach = stamps ?: generator.stampIndex.overlapping(chunk.cornerX, chunk.cornerZ, chunk.cornerX + q * chunk.spacing, chunk.cornerZ + q * chunk.spacing)
        val scratch = FloatArray(biomes.size)
        chunk.scatter = ScatterPlacer.place(chunk, generator.scatterEntries, generator.projectSeed, generator.seedName, generator.seedOffset, reach) { b, x, z, h ->
            if (painted != null) {
                val pi = ((x - chunk.cornerX) / chunk.spacing + 0.5f).toInt().coerceIn(0, q)
                val pj = ((z - chunk.cornerZ) / chunk.spacing + 0.5f).toInt().coerceIn(0, q)
                paintedWeights(painted, n, pi, pj, scratch)[b]
            } else map.weightsAt(x, z, h, scratch)[b]
        }
    }

    /** Painted ids blurred over the 3 × 3 samples around (i, j) (soft painted borders, REN-28 §6). */
    private fun paintedWeights(ids: ByteArray, n: Int, i: Int, j: Int, out: FloatArray): FloatArray {
        out.fill(0f)
        var count = 0
        for (dj in -1..1) for (di in -1..1) {
            val a = (i + di).coerceIn(0, n - 1); val b = (j + dj).coerceIn(0, n - 1)
            val id = ids[b * n + a].toInt() and 255
            if (id in out.indices) { out[id] += 1f; count++ }
        }
        if (count == 0) out[0] = 1f else for (k in out.indices) out[k] /= count
        return out
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
