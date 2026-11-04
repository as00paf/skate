package com.pafoid.skate.engine.gameplay.camera

import com.pafoid.skate.engine.utils.CameraMotion
import org.joml.Vector3f
import kotlin.math.sin

/**
 * Trauma-based camera shake. Impacts add trauma (0..1), which decays linearly. The shake amplitude grows with
 * trauma², so small knocks barely move the camera and big ones shake it hard. The motion is smooth deterministic
 * noise, which keeps it testable.
 */
object CameraShake {

    fun add(trauma: Float, amount: Float): Float = (trauma + amount).coerceIn(0f, 1f)

    /** Trauma after [dt] seconds of decaying at [rate] per second. */
    fun decay(trauma: Float, rate: Float, dt: Float): Float = (trauma - rate * CameraMotion.clampDt(dt)).coerceAtLeast(0f)

    /** Positional shake in metres at [time] seconds, each axis within ±[maxOffset] × trauma². */
    fun offset(trauma: Float, time: Float, frequency: Float, maxOffset: Float, dest: Vector3f = Vector3f()): Vector3f {
        val k = maxOffset * trauma * trauma
        val t = time * frequency
        return dest.set(k * noise(t, 1.3f), k * noise(t, 7.1f), k * noise(t, 13.7f))
    }

    /** Roll shake in degrees, within ±[maxDegrees] × trauma². */
    fun roll(trauma: Float, time: Float, frequency: Float, maxDegrees: Float): Float =
        maxDegrees * trauma * trauma * noise(time * frequency, 21.9f)

    /** Smooth pseudo-noise in [-1, 1]: three incommensurate sine waves, phase-shifted by [seed]. */
    fun noise(t: Float, seed: Float): Float =
        (sin(t + seed) + 0.5f * sin(2.31f * t + 1.7f * seed) + 0.25f * sin(4.13f * t + 2.9f * seed)) / 1.75f
}
