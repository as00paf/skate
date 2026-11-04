package com.pafoid.skate.engine.utils

import kotlin.math.exp

/**
 * Frame-rate independent camera motion (from EDT-34), shared by the editor camera and runtime camera components.
 * Keyboard motion and animated transitions are scaled by the real frame time, so a camera covers the same distance
 * per second at any FPS; mouse look uses input deltas, which do not depend on the frame rate.
 */
object CameraMotion {
    /** Longest frame time applied in one step, so a hitch (loading, breakpoint) cannot fling the camera. */
    const val MAX_DT = 0.1f

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
