package com.pafoid.skate.engine.render.water

import kotlin.math.ceil
import kotlin.math.min

/**
 * One chunk of a water surface grid (REN-29 §6.1), in the water's local XZ (centred on the entity).
 *
 * [vertices] holds xyz triples: the grid on y = 0, then the skirt copies of the border vertices on y = -1 (the vertex
 * shader drops them by the skirt depth, hiding cracks between chunks drawn at different LODs). [lodIndices] holds one
 * triangle list per LOD (vertex step 1, 2, 4), skirts included.
 */
class WaterChunk(
    val minX: Float,
    val minZ: Float,
    val maxX: Float,
    val maxZ: Float,
    val quadsX: Int,
    val quadsZ: Int,
    val vertices: FloatArray,
    val lodIndices: List<IntArray>,
) {
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerZ: Float get() = (minZ + maxZ) * 0.5f
    val gridVertexCount: Int get() = (quadsX + 1) * (quadsZ + 1)
    val vertexCount: Int get() = vertices.size / 3
}

/** A water surface: its chunks and the grid spacing they were built with. */
class WaterGrid(val sizeX: Float, val sizeZ: Float, val quadSize: Float, val chunks: List<WaterChunk>)

/** Builds water surface grids (REN-29 §6.1). Pure: the GPU side is `WaterSurfaceMesh`. */
object WaterMeshBuilder {
    const val CHUNK_QUADS = 32
    val LOD_STEPS = intArrayOf(1, 2, 4)

    /** Smallest quad size accepted, so a typo can't build millions of vertices. */
    const val MIN_QUAD_SIZE = 0.05f

    /**
     * A [sizeX] × [sizeZ] m grid centred on the origin with quads of [quadSize] m, in chunks of up to
     * [CHUNK_QUADS] × [CHUNK_QUADS] quads. The last quad of a side is shorter when the size is not a multiple.
     */
    fun build(sizeX: Float, sizeZ: Float, quadSize: Float): WaterGrid {
        require(sizeX > 0f && sizeZ > 0f) { "Water size must be positive, got $sizeX x $sizeZ" }
        val quad = quadSize.coerceAtLeast(MIN_QUAD_SIZE)
        val totalX = ceil(sizeX / quad - 1e-4f).toInt().coerceAtLeast(1)
        val totalZ = ceil(sizeZ / quad - 1e-4f).toInt().coerceAtLeast(1)
        val halfX = sizeX * 0.5f
        val halfZ = sizeZ * 0.5f
        fun x(i: Int) = min(-halfX + i * quad, halfX)
        fun z(j: Int) = min(-halfZ + j * quad, halfZ)

        val chunks = ArrayList<WaterChunk>()
        var cj = 0
        while (cj < totalZ) {
            val nz = min(CHUNK_QUADS, totalZ - cj)
            var ci = 0
            while (ci < totalX) {
                val nx = min(CHUNK_QUADS, totalX - ci)
                chunks += chunk(ci, cj, nx, nz, ::x, ::z)
                ci += nx
            }
            cj += nz
        }
        return WaterGrid(sizeX, sizeZ, quad, chunks)
    }

    /** Sampled grid lines for a side of [n] quads at vertex step [step]: 0, step, 2·step, …, always ending on [n]. */
    fun samples(n: Int, step: Int): IntArray {
        val out = ArrayList<Int>()
        var i = 0
        while (i < n) { out += i; i += step }
        out += n
        return out.toIntArray()
    }

    private fun chunk(i0: Int, j0: Int, nx: Int, nz: Int, x: (Int) -> Float, z: (Int) -> Float): WaterChunk {
        val grid = (nx + 1) * (nz + 1)
        // Skirt vertices: one per border vertex, per side (corners appear on two sides)
        val skirtCount = 2 * (nx + 1) + 2 * (nz + 1)
        val v = FloatArray((grid + skirtCount) * 3)
        fun gridIndex(i: Int, j: Int) = j * (nx + 1) + i
        for (j in 0..nz) for (i in 0..nx) {
            val o = gridIndex(i, j) * 3
            v[o] = x(i0 + i); v[o + 1] = 0f; v[o + 2] = z(j0 + j)
        }
        // Skirt rows: bottom (j = 0), top (j = nz), left (i = 0), right (i = nx)
        val bottom = grid
        val top = bottom + (nx + 1)
        val left = top + (nx + 1)
        val right = left + (nz + 1)
        fun putSkirt(at: Int, i: Int, j: Int) {
            val src = gridIndex(i, j) * 3
            v[at * 3] = v[src]; v[at * 3 + 1] = -1f; v[at * 3 + 2] = v[src + 2]
        }
        for (i in 0..nx) { putSkirt(bottom + i, i, 0); putSkirt(top + i, i, nz) }
        for (j in 0..nz) { putSkirt(left + j, 0, j); putSkirt(right + j, nx, j) }

        val lods = LOD_STEPS.map { step ->
            val xs = samples(nx, step)
            val zs = samples(nz, step)
            val idx = ArrayList<Int>()
            for (b in 0 until zs.size - 1) for (a in 0 until xs.size - 1) {
                val v00 = gridIndex(xs[a], zs[b]); val v10 = gridIndex(xs[a + 1], zs[b])
                val v01 = gridIndex(xs[a], zs[b + 1]); val v11 = gridIndex(xs[a + 1], zs[b + 1])
                idx += listOf(v00, v01, v10, v10, v01, v11) // counter-clockwise seen from above
            }
            for (a in 0 until xs.size - 1) {
                skirt(idx, gridIndex(xs[a], 0), gridIndex(xs[a + 1], 0), bottom + xs[a], bottom + xs[a + 1])
                skirt(idx, gridIndex(xs[a + 1], nz), gridIndex(xs[a], nz), top + xs[a + 1], top + xs[a])
            }
            for (b in 0 until zs.size - 1) {
                skirt(idx, gridIndex(0, zs[b + 1]), gridIndex(0, zs[b]), left + zs[b + 1], left + zs[b])
                skirt(idx, gridIndex(nx, zs[b]), gridIndex(nx, zs[b + 1]), right + zs[b], right + zs[b + 1])
            }
            idx.toIntArray()
        }
        return WaterChunk(x(i0), z(j0), x(i0 + nx), z(j0 + nz), nx, nz, v, lods)
    }

    /** A vertical quad hanging from the surface edge a→b down to its skirt copies sa→sb. */
    private fun skirt(out: MutableList<Int>, a: Int, b: Int, sa: Int, sb: Int) {
        out += listOf(a, sa, b, b, sa, sb)
    }
}
