package com.pafoid.skate.engine.assets.loaders

import kotlin.math.sqrt

/**
 * AST-24: vertex normals for meshes imported without usable ones. `aiProcess_GenSmoothNormals` only fills meshes that
 * have no normals at all, so a file whose normals are all zero (`vn 0 0 0`) keeps them and renders with a NaN normal.
 * [AssimpLoader] checks [needsRegeneration] and replaces them with [smooth].
 */
object MeshNormals {
    private const val EPSILON_SQ = 1e-12f

    /** True when [normals] (xyz per vertex) has no non-zero normal: missing in the file. */
    fun needsRegeneration(normals: FloatArray): Boolean {
        for (i in 0 until normals.size / 3) {
            val x = normals[i * 3]
            val y = normals[i * 3 + 1]
            val z = normals[i * 3 + 2]
            if (x * x + y * y + z * z > EPSILON_SQ) return false
        }
        return true
    }

    /**
     * Smooth normals from [positions] (xyz per vertex) and triangle [indices] (counter-clockwise, Assimp's default):
     * the area-weighted sum of the face normals around each position. Vertices at the same position (split by UV
     * seams) share one normal, so seams do not show. Degenerate triangles add nothing; a vertex with no area around
     * it gets (0, 1, 0).
     */
    fun smooth(positions: FloatArray, indices: IntArray): FloatArray {
        val vertexCount = positions.size / 3
        val groupOf = IntArray(vertexCount)
        val groups = HashMap<Triple<Float, Float, Float>, Int>()
        for (v in 0 until vertexCount) {
            // + 0f turns -0.0 into 0.0: Float keys compare them as different values.
            val key = Triple(positions[v * 3] + 0f, positions[v * 3 + 1] + 0f, positions[v * 3 + 2] + 0f)
            groupOf[v] = groups.getOrPut(key) { groups.size }
        }

        val sums = FloatArray(groups.size * 3)
        for (t in 0 until indices.size / 3) {
            val a = indices[t * 3]
            val b = indices[t * 3 + 1]
            val c = indices[t * 3 + 2]
            val e1x = positions[b * 3] - positions[a * 3]
            val e1y = positions[b * 3 + 1] - positions[a * 3 + 1]
            val e1z = positions[b * 3 + 2] - positions[a * 3 + 2]
            val e2x = positions[c * 3] - positions[a * 3]
            val e2y = positions[c * 3 + 1] - positions[a * 3 + 1]
            val e2z = positions[c * 3 + 2] - positions[a * 3 + 2]
            // Cross product length is twice the triangle area: larger faces weigh more.
            val nx = e1y * e2z - e1z * e2y
            val ny = e1z * e2x - e1x * e2z
            val nz = e1x * e2y - e1y * e2x
            for (corner in intArrayOf(a, b, c)) {
                val g = groupOf[corner] * 3
                sums[g] += nx
                sums[g + 1] += ny
                sums[g + 2] += nz
            }
        }

        val normals = FloatArray(vertexCount * 3)
        for (v in 0 until vertexCount) {
            val g = groupOf[v] * 3
            val x = sums[g]
            val y = sums[g + 1]
            val z = sums[g + 2]
            val lengthSq = x * x + y * y + z * z
            if (lengthSq > 0f) {
                val inv = 1f / sqrt(lengthSq)
                normals[v * 3] = x * inv
                normals[v * 3 + 1] = y * inv
                normals[v * 3 + 2] = z * inv
            } else {
                normals[v * 3 + 1] = 1f
            }
        }
        return normals
    }
}
