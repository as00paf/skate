package com.pafoid.skate.engine.terrain

import org.joml.Vector3f
import kotlin.math.floor

/**
 * The single terrain height sampler (REN-28 §3.2), shared by rendering, scatter, gameplay queries and the collider.
 * Coordinates are terrain-local metres (relative to the terrain's position); heights too.
 */
interface HeightField {
    fun heightAt(x: Float, z: Float): Float
    fun normalAt(x: Float, z: Float, out: Vector3f): Vector3f

    /** True when full-resolution data is loaded at ([x], [z]); false on the fallback (or outside the terrain). */
    fun isDetailed(x: Float, z: Float): Boolean
}

/**
 * [HeightField] over the resident chunks, falling back to [fallback] where a chunk is missing (the generator for
 * procedural terrains now; the always-resident overview with streaming, REN-28 §15). Inside a quad it interpolates on
 * the two triangles split along the (x, z)–(x+1, z+1) diagonal, the split the mesh and `ColliderShape.Heightfield`
 * use, so queries match what is drawn and collided with exactly at LOD 0.
 */
class ChunkedHeightField(
    val layout: TerrainLayout,
    private val chunks: (cx: Int, cz: Int) -> TerrainChunkData?,
    private val fallback: (x: Float, z: Float) -> Float = { _, _ -> 0f },
) : HeightField {

    override fun heightAt(x: Float, z: Float): Float {
        val cx = layout.chunkX(x)
        val cz = layout.chunkZ(z)
        val chunk = (if (layout.contains(x, z)) chunks(cx, cz) else null) ?: return fallback(x, z)
        val q = chunk.chunkQuads
        val fx = ((x - chunk.cornerX) / chunk.spacing).coerceIn(0f, q.toFloat())
        val fz = ((z - chunk.cornerZ) / chunk.spacing).coerceIn(0f, q.toFloat())
        val i = floor(fx).toInt().coerceAtMost(q - 1)
        val j = floor(fz).toInt().coerceAtMost(q - 1)
        val u = fx - i
        val v = fz - j
        val h00 = chunk.height(i, j)
        val h11 = chunk.height(i + 1, j + 1)
        return if (u >= v) {
            // Triangle (i, j), (i+1, j), (i+1, j+1)
            h00 + (chunk.height(i + 1, j) - h00) * u + (h11 - chunk.height(i + 1, j)) * v
        } else {
            // Triangle (i, j), (i, j+1), (i+1, j+1)
            h00 + (chunk.height(i, j + 1) - h00) * v + (h11 - chunk.height(i, j + 1)) * u
        }
    }

    override fun normalAt(x: Float, z: Float, out: Vector3f): Vector3f {
        val e = layout.spacing
        val dx = heightAt(x + e, z) - heightAt(x - e, z)
        val dz = heightAt(x, z + e) - heightAt(x, z - e)
        return out.set(-dx, 2f * e, -dz).normalize()
    }

    override fun isDetailed(x: Float, z: Float): Boolean =
        layout.contains(x, z) && chunks(layout.chunkX(x), layout.chunkZ(z)) != null
}
