package com.pafoid.skate.engine.render.lod

/**
 * Screen-space error LOD selection, shared by water (REN-29 §6.1) and terrain (REN-28 `TerrainLod`). A level with
 * geometric error `e` metres seen at distance `d` covers `e × projectionScale / d` pixels, where
 * `projectionScale = viewportHeight / (2 tan(fovY / 2))`. The coarsest level within [maxPixelError] wins.
 */
object ScreenErrorLod {
    /** Projection scale in pixels per metre at one metre: `viewportHeight / (2 tan(fovY / 2))`. */
    fun projectionScale(viewportHeight: Int, fovYDegrees: Float): Float =
        viewportHeight / (2f * kotlin.math.tan(Math.toRadians(fovYDegrees / 2.0)).toFloat())

    /** Pixels covered by a geometric error of [error] metres at [distance] metres. */
    fun pixelError(error: Float, distance: Float, projectionScale: Float): Float =
        error * projectionScale / distance.coerceAtLeast(MIN_DISTANCE)

    /**
     * The coarsest level (index into [errors], finest first, increasing) whose error stays within [maxPixelError] at
     * [distance]. Level 0 is always allowed.
     */
    fun select(errors: FloatArray, distance: Float, projectionScale: Float, maxPixelError: Float): Int {
        var level = 0
        for (i in 1 until errors.size) {
            if (pixelError(errors[i], distance, projectionScale) <= maxPixelError) level = i else break
        }
        return level
    }

    private const val MIN_DISTANCE = 1e-3f
}
