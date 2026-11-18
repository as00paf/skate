package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.terrain.TerrainChunkData
import org.joml.Vector3f
import kotlin.math.abs

/**
 * Terrain chunk geometry (REN-28 §4.1), pure. Every chunk of a terrain shares one grid vertex buffer ([gridVertices]:
 * sample column i, row j and a skirt flag) and the LOD index buffers ([lodIndices], vertex step 2^lod, skirts included);
 * a chunk only adds its stream ([stream]: height, normal x, normal z per vertex). Quads split along the
 * (i, j)–(i+1, j+1) diagonal, as `ColliderShape.Heightfield` does, so LOD 0 is exactly the collided surface.
 */
class TerrainMeshBuilder(val chunkQuads: Int) {
    init {
        require(chunkQuads >= 2 && Integer.bitCount(chunkQuads) == 1) { "chunkQuads must be a power of two >= 2" }
    }

    private val n = chunkQuads + 1

    /** Grid vertices, then one skirt vertex per border vertex: bottom (j = 0), top (j = q), left (i = 0), right (i = q). */
    val vertexCount: Int = n * n + 4 * n
    private val bottom = n * n
    private val top = bottom + n
    private val left = top + n
    private val right = left + n

    /** LOD count: steps 1, 2, 4, 8 (fewer for small chunks, so the coarsest level still has a quad). */
    val lodCount: Int = minOf(MAX_LODS, Integer.numberOfTrailingZeros(chunkQuads) + 1)

    fun gridIndex(i: Int, j: Int) = j * n + i

    /** For each vertex: (i, j, skirt) with skirt 1 on the skirt copies. */
    fun gridVertices(): FloatArray {
        val out = FloatArray(vertexCount * 3)
        for (j in 0 until n) for (i in 0 until n) put(out, gridIndex(i, j), i, j, 0f)
        for (k in 0 until n) {
            put(out, bottom + k, k, 0, 1f); put(out, top + k, k, chunkQuads, 1f)
            put(out, left + k, 0, k, 1f); put(out, right + k, chunkQuads, k, 1f)
        }
        return out
    }

    private fun put(out: FloatArray, v: Int, i: Int, j: Int, skirt: Float) {
        out[v * 3] = i.toFloat(); out[v * 3 + 1] = j.toFloat(); out[v * 3 + 2] = skirt
    }

    /** Triangle indices of LOD [lod] (step 2^lod): surface counter-clockwise from above, then the four skirts. */
    fun lodIndices(lod: Int): IntArray {
        val step = 1 shl lod
        val idx = ArrayList<Int>()
        var j = 0
        while (j < chunkQuads) {
            var i = 0
            while (i < chunkQuads) {
                val v00 = gridIndex(i, j); val v10 = gridIndex(i + step, j)
                val v01 = gridIndex(i, j + step); val v11 = gridIndex(i + step, j + step)
                idx += listOf(v00, v01, v11, v00, v11, v10)
                i += step
            }
            j += step
        }
        var k = 0
        while (k < chunkQuads) {
            val k2 = k + step
            quad(idx, gridIndex(k, 0), gridIndex(k2, 0), bottom + k, bottom + k2)
            quad(idx, gridIndex(k2, chunkQuads), gridIndex(k, chunkQuads), top + k2, top + k)
            quad(idx, gridIndex(0, k2), gridIndex(0, k), left + k2, left + k)
            quad(idx, gridIndex(chunkQuads, k), gridIndex(chunkQuads, k2), right + k, right + k2)
            k = k2
        }
        return idx.toIntArray()
    }

    private fun quad(out: MutableList<Int>, a: Int, b: Int, sa: Int, sb: Int) {
        out += listOf(a, sa, b, b, sa, sb)
    }

    /** The chunk's per-vertex stream (height, normal x, normal z), skirt copies included; into [out] if it fits. */
    fun stream(chunk: TerrainChunkData, out: FloatArray = FloatArray(vertexCount * STREAM_FLOATS)): FloatArray {
        require(chunk.chunkQuads == chunkQuads)
        val normal = Vector3f()
        fun write(v: Int, i: Int, j: Int) {
            chunk.normal(i, j, normal)
            out[v * 3] = chunk.height(i, j); out[v * 3 + 1] = normal.x; out[v * 3 + 2] = normal.z
        }
        for (j in 0 until n) for (i in 0 until n) write(gridIndex(i, j), i, j)
        for (k in 0 until n) {
            write(bottom + k, k, 0); write(top + k, k, chunkQuads)
            write(left + k, 0, k); write(right + k, chunkQuads, k)
        }
        return out
    }

    /**
     * Largest height difference (m) between the full-resolution samples and LOD [lod]'s triangles over them, per LOD
     * (LOD 0 is 0). Used for screen-space error selection and the skirt drop.
     */
    fun lodErrors(chunk: TerrainChunkData): FloatArray = FloatArray(lodCount) { lod ->
        if (lod == 0) 0f else {
            val step = 1 shl lod
            var worst = 0f
            for (j in 0..chunkQuads) for (i in 0..chunkQuads) {
                val ci = minOf(i / step * step, chunkQuads - step); val cj = minOf(j / step * step, chunkQuads - step)
                val u = (i - ci).toFloat() / step; val v = (j - cj).toFloat() / step
                val h00 = chunk.height(ci, cj); val h11 = chunk.height(ci + step, cj + step)
                val coarse = if (u >= v) {
                    val h10 = chunk.height(ci + step, cj); h00 + (h10 - h00) * u + (h11 - h10) * v
                } else {
                    val h01 = chunk.height(ci, cj + step); h00 + (h01 - h00) * v + (h11 - h01) * u
                }
                worst = maxOf(worst, abs(coarse - chunk.height(i, j)))
            }
            worst
        }
    }

    companion object {
        const val MAX_LODS = 4
        const val STREAM_FLOATS = 3
    }
}
