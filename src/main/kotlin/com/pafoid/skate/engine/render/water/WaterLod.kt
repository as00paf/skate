package com.pafoid.skate.engine.render.water

import com.pafoid.skate.engine.render.lod.ScreenErrorLod

/**
 * Water chunk LOD (REN-29 §6.1): the geometric error of skipping vertices grows with the step and the wave amplitude
 * (flat water coarsens quickly), then [ScreenErrorLod] picks the level at the chunk's distance.
 */
object WaterLod {
    const val MAX_PIXEL_ERROR = 2f

    /** Geometric error (m) per LOD level for a grid of [quadSize] with waves summing to [totalAmplitude]. */
    fun errors(quadSize: Float, totalAmplitude: Float, out: FloatArray = FloatArray(WaterMeshBuilder.LOD_STEPS.size)): FloatArray {
        val roughness = MIN_ROUGHNESS + totalAmplitude
        for (i in out.indices) out[i] = quadSize * (WaterMeshBuilder.LOD_STEPS[i] - 1) * roughness
        return out
    }

    fun select(errors: FloatArray, distance: Float, projectionScale: Float): Int =
        ScreenErrorLod.select(errors, distance, projectionScale, MAX_PIXEL_ERROR)

    /** Even mirror-flat water keeps some detail for reflections and foam. */
    private const val MIN_ROUGHNESS = 0.05f
}
