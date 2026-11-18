package com.pafoid.skate.engine.terrain

import org.joml.Vector3f

/**
 * One chunk's CPU data (REN-28 §3.2): heights in metres (terrain-local, add the Transform's Y for world) on a
 * `(chunkQuads + 3)²` grid that includes a one-sample apron, so edge normals match the neighbours. Sample (i, j) with
 * i, j in `-1..chunkQuads + 1` sits at local `(cornerX + i × spacing, cornerZ + j × spacing)`.
 */
class TerrainChunkData(
    val cx: Int,
    val cz: Int,
    val chunkQuads: Int,
    val spacing: Float,
    val cornerX: Float,
    val cornerZ: Float,
    val heights: FloatArray,
) {
    val stride: Int = chunkQuads + 3

    init {
        require(heights.size == stride * stride) { "Expected ${stride * stride} heights, got ${heights.size}" }
    }

    /** Set when the heights changed and derived data (mesh, collider, scatter) must be rebuilt. */
    var dirty = true

    /** Height of sample (i, j), apron included (i, j in -1..chunkQuads + 1). */
    fun height(i: Int, j: Int): Float = heights[(j + 1) * stride + (i + 1)]

    fun setHeight(i: Int, j: Int, value: Float) {
        heights[(j + 1) * stride + (i + 1)] = value
        dirty = true
    }

    /** Lowest and highest height over the chunk's own samples (no apron), for its AABB. */
    val minHeight: Float get() = range().first
    val maxHeight: Float get() = range().second

    private fun range(): Pair<Float, Float> {
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (j in 0..chunkQuads) for (i in 0..chunkQuads) { val h = height(i, j); if (h < lo) lo = h; if (h > hi) hi = h }
        return lo to hi
    }

    /**
     * The chunk's own `(chunkQuads + 1)²` samples, row-major by Z, for `ColliderShape.Heightfield` (PHY-15): its local
     * origin is this chunk's corner sample, at `(cornerX, 0, cornerZ)` from the terrain's position.
     */
    fun colliderHeights(): FloatArray {
        val n = chunkQuads + 1
        val out = FloatArray(n * n)
        for (j in 0 until n) for (i in 0 until n) out[j * n + i] = height(i, j)
        return out
    }

    /** Normal at sample (i, j), i, j in `0..chunkQuads`, from central differences over the apron. */
    fun normal(i: Int, j: Int, out: Vector3f): Vector3f {
        val dx = height(i + 1, j) - height(i - 1, j)
        val dz = height(i, j + 1) - height(i, j - 1)
        return out.set(-dx, 2f * spacing, -dz).normalize()
    }
}
