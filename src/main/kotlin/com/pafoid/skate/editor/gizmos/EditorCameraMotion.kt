package com.pafoid.skate.editor.gizmos

import kotlin.math.exp

/**
 * Frame-rate independent motion for the editor camera. Keyboard motion and animated transitions are scaled by
 * the real frame time, so the camera covers the same distance per second at any FPS. Mouse-driven motion (look,
 * orbit) and wheel zoom use input deltas, which do not depend on the frame rate.
 */
object EditorCameraMotion {
    /** Longest frame time applied in one step, so a hitch (loading, breakpoint) cannot fling the camera. */
    const val MAX_DT = 0.1f

    /** WASD / up-down fly speed, world units per second. */
    const val FLY_SPEED = 6f

    /** How fast the reset-view transition converges, per second (larger = snappier). */
    const val RESET_SHARPNESS = 8f

    /** Distance below which the reset transition snaps to its target. */
    const val RESET_EPSILON = 0.01f

    /** [dt] limited to [0, [MAX_DT]]; negative or non-finite frame times move nothing. */
    fun clampDt(dt: Float): Float = if (dt.isFinite()) dt.coerceIn(0f, MAX_DT) else 0f

    /** Distance travelled this frame at [speed] units per second. */
    fun step(speed: Float, dt: Float): Float = speed * clampDt(dt)

    /**
     * Fraction of the remaining distance to cover this frame when approaching a target exponentially at
     * [sharpness] per second. Two frames of dt/2 give the same result as one frame of dt.
     */
    fun smoothing(sharpness: Float, dt: Float): Float = 1f - exp(-sharpness * clampDt(dt))
}
