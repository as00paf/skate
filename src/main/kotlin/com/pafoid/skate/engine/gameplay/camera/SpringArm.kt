package com.pafoid.skate.engine.gameplay.camera

import com.pafoid.skate.engine.utils.CameraMotion

/**
 * Spring arm between a camera's pivot and the camera: a sphere of the probe radius is swept from the pivot to where
 * the camera wants to be, and the arm stops where it hits.
 */
object SpringArm {

    /**
     * Arm length allowed for a [desired] arm whose probe stopped at [hitFraction] (0..1 of the sweep; null when
     * nothing was hit). It is never shorter than [minLength] (or [desired], if that is shorter).
     */
    fun clampedLength(desired: Float, hitFraction: Float?, minLength: Float): Float {
        if (hitFraction == null || !hitFraction.isFinite()) return desired
        val floor = minOf(minLength, desired)
        return (desired * hitFraction.coerceIn(0f, 1f)).coerceIn(floor, desired)
    }

    /**
     * Next arm length from [current] toward [allowed]. The arm shortens at once, so the camera never sees through
     * the obstacle for a frame. It lengthens smoothly at [returnSharpness] per second once the way is clear.
     */
    fun smoothed(current: Float, allowed: Float, returnSharpness: Float, dt: Float): Float =
        if (allowed <= current) allowed else current + (allowed - current) * CameraMotion.smoothing(returnSharpness, dt)
}
