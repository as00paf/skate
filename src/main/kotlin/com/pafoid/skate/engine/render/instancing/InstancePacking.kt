package com.pafoid.skate.engine.render.instancing

import com.pafoid.skate.engine.render.data.VertexLayouts
import org.joml.Matrix4f

/**
 * Packs one instance record (REN-29 §3.6): the world matrix's first three rows (12 floats, row-major, the bottom row
 * is always 0 0 0 1), then tint, random (0..1), wind phase and LOD fade. 16 floats, 64 B.
 */
object InstancePacking {
    const val FLOATS = VertexLayouts.INSTANCE_FLOATS

    fun put(
        world: Matrix4f, out: FloatArray, offset: Int,
        tint: Float = 0f, random: Float = 0f, windPhase: Float = 0f, lodFade: Float = 1f,
    ) {
        var o = offset
        for (row in 0 until 3) {
            out[o++] = world.get(0, row); out[o++] = world.get(1, row); out[o++] = world.get(2, row); out[o++] = world.get(3, row)
        }
        out[o++] = tint; out[o++] = random; out[o++] = windPhase; out[o] = lodFade
    }

    /** Reads instance [index] of [data] back into [out] (tests, CPU-side culling). */
    fun matrix(data: FloatArray, index: Int, out: Matrix4f): Matrix4f {
        val o = index * FLOATS
        return out.set(
            data[o], data[o + 4], data[o + 8], 0f,
            data[o + 1], data[o + 5], data[o + 9], 0f,
            data[o + 2], data[o + 6], data[o + 10], 0f,
            data[o + 3], data[o + 7], data[o + 11], 1f,
        )
    }
}
