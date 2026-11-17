package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.render.lod.ScreenErrorLod

/**
 * Terrain chunk LOD (REN-28 §4.1): the coarsest level whose error projects under [PIXEL_ERROR] at the chunk's nearest
 * point, with [HYSTERESIS] pixels of hysteresis so a chunk at a threshold does not flicker; skirts drop by
 * `max(1 m, 2 × error)`.
 */
object TerrainLod {
    const val PIXEL_ERROR = 2f
    const val HYSTERESIS = 0.5f

    fun select(errors: FloatArray, distance: Float, projectionScale: Float, previous: Int = -1): Int {
        val candidate = ScreenErrorLod.select(errors, distance, projectionScale, PIXEL_ERROR)
        if (previous !in errors.indices || candidate == previous) return candidate
        return if (candidate > previous) {
            // Coarser: only once clearly under the threshold
            val strict = ScreenErrorLod.select(errors, distance, projectionScale, PIXEL_ERROR - HYSTERESIS)
            if (strict > previous) strict else previous
        } else {
            // Finer: keep the current level while it stays within the threshold plus the margin
            if (ScreenErrorLod.pixelError(errors[previous], distance, projectionScale) <= PIXEL_ERROR + HYSTERESIS) previous else candidate
        }
    }

    fun skirtDrop(errors: FloatArray, lod: Int): Float = maxOf(1f, 2f * errors[lod.coerceIn(0, errors.size - 1)])
}
