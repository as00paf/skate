package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.engine.utils.CameraMotion

/**
 * Frame-rate independent motion for the editor camera. Keyboard motion and animated transitions are scaled by
 * the real frame time, so the camera covers the same distance per second at any FPS. Mouse-driven motion (look,
 * orbit) and wheel zoom use input deltas, which do not depend on the frame rate.
 */
object EditorCameraMotion {
    /** Longest frame time applied in one step, so a hitch (loading, breakpoint) cannot fling the camera. */
    const val MAX_DT = CameraMotion.MAX_DT

    /** WASD / up-down fly speed, world units per second. */
    const val FLY_SPEED = 6f

    /** How fast the reset-view transition converges, per second (larger = snappier). */
    const val RESET_SHARPNESS = 8f

    /** Distance below which the reset transition snaps to its target. */
    const val RESET_EPSILON = 0.01f

    /** [dt] limited to [0, [MAX_DT]]; negative or non-finite frame times move nothing. */
    fun clampDt(dt: Float): Float = CameraMotion.clampDt(dt)

    /** Distance travelled this frame at [speed] units per second. */
    fun step(speed: Float, dt: Float): Float = CameraMotion.step(speed, dt)

    /**
     * Fraction of the remaining distance to cover this frame when approaching a target exponentially at
     * [sharpness] per second. Two frames of dt/2 give the same result as one frame of dt.
     */
    fun smoothing(sharpness: Float, dt: Float): Float = CameraMotion.smoothing(sharpness, dt)
}
