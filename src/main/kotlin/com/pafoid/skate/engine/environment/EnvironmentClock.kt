package com.pafoid.skate.engine.environment

/**
 * Animation clock for wind and water (REN-29 §5.3), stepped by `EnvironmentClockSystem`. Rendering and buoyancy read
 * the same clock, so what floats is what you see.
 *
 * - In Play it counts fixed ticks: `time = ticks × fixedDt`, identical on every machine for the same tick.
 * - In edit mode it advances by the fixed step while [animateInEditor] is on, and sits at 0 when it is off.
 * - Paused: the system does not step, so the clock is frozen (no catch-up).
 */
class EnvironmentClock {
    /** Fixed ticks since Play started (0 in edit mode). */
    var ticks = 0L
        private set

    /** Seconds of animation at the last fixed step. */
    var animationTime = 0f
        private set

    /** Fixed step length used by the last [step]. */
    var fixedDt = 0f
        private set

    private var wasRunning = false
    private var frozen = false

    /** Fraction of a fixed step elapsed since the last one, in [0, 1] (the system manager's interpolation factor). */
    var stepAlpha: () -> Float = { 0f }

    fun step(fixedDt: Float, running: Boolean, animateInEditor: Boolean) {
        this.fixedDt = fixedDt
        if (running != wasRunning) {
            // Entering or leaving Play restarts the clock: every Play run replays the same waves
            ticks = 0
            animationTime = 0f
            wasRunning = running
        }
        frozen = !running && !animateInEditor
        when {
            running -> { ticks++; animationTime = ticks * fixedDt }
            frozen -> animationTime = 0f
            else -> animationTime += fixedDt
        }
    }

    /** Time for this frame's rendering: the last step plus the interpolated part of the next one. */
    fun renderTime(): Float = if (frozen || fixedDt == 0f) animationTime else animationTime + stepAlpha().coerceIn(0f, 1f) * fixedDt

    fun reset() {
        ticks = 0
        animationTime = 0f
        wasRunning = false
        frozen = false
    }
}
