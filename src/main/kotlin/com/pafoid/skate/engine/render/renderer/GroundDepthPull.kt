package com.pafoid.skate.engine.render.renderer

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * How far `debugLine2D.glsl` pulls a debug vertex toward the camera (view depth, metres) so it wins the depth test
 * against the surface it lies on (REN-18, REN-19). CPU mirror of the shader for tests; keep the two in sync.
 *
 * - Every vertex: [BASE_PULL] of its depth (covers depth-buffer precision).
 * - Ground lines (the editor grid, on a horizontal floor): plus the floor's depth change across [GROUND_PULL_PIXELS]
 *   pixels. Seen at a grazing angle, one pixel of a floor [heightDiff] below the camera spans about
 *   `depth^2 * pixelAngle / heightDiff` of depth, far more than the base pull. That made far grid lines z-fight with
 *   the floor near its edge (about 0.2 m per pixel at 30 m from 5 m up, versus a 0.03 m base pull).
 */
object GroundDepthPull {
    const val BASE_PULL = 0.001f
    const val GROUND_PULL_PIXELS = 2.5f
    private const val MIN_HEIGHT = 0.05f
    private const val MAX_FRACTION = 0.5f

    /** Angle in radians covered by one pixel at the centre of a perspective view. */
    fun pixelAngle(fovYRadians: Float, viewportHeightPx: Int): Float = 2f * tan(fovYRadians / 2f) / max(viewportHeightPx, 1)

    fun viewDepthPull(depth: Float, heightDiff: Float, pixelAngle: Float, ground: Boolean): Float {
        if (depth <= 0f) return 0f
        var pull = depth * BASE_PULL
        if (ground) pull += GROUND_PULL_PIXELS * depth * depth * pixelAngle / max(abs(heightDiff), MIN_HEIGHT)
        return min(pull, depth * MAX_FRACTION)
    }
}
