package com.pafoid.skate.engine.environment

import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Scene wind (REN-29 §5.1), pure so gameplay, particles, audio and water can sample it without the renderer.
 * `wind.glsl` evaluates exactly the same function (sines, no noise texture); `WindFieldTest` pins a table of values.
 *
 * The wind blows towards [directionDeg] (yaw: 0° = +Z, 90° = +X) at [speed] m/s. Gust fronts, a sum of 3 sines along
 * the wind with a cross-wind modulation, roll across the world at [speed]: `sample = dir × speed × (1 + gustStrength × gust)`.
 */
class WindField(
    directionDeg: Float = 45f,
    var speed: Float = 3f,
    var gustStrength: Float = 0.4f,
    var gustScale: Float = 20f,
    var turbulence: Float = 0.3f,
) {
    /** Unit direction on XZ the wind blows towards. */
    var dirX = 0f
        private set
    var dirZ = 0f
        private set

    var directionDeg: Float = directionDeg
        set(value) {
            field = value
            val rad = Math.toRadians(value.toDouble())
            dirX = sin(rad).toFloat()
            dirZ = cos(rad).toFloat()
        }

    init {
        this.directionDeg = directionDeg
    }

    fun set(directionDeg: Float, speed: Float, gustStrength: Float, gustScale: Float, turbulence: Float): WindField {
        this.directionDeg = directionDeg
        this.speed = speed
        this.gustStrength = gustStrength
        this.gustScale = gustScale
        this.turbulence = turbulence
        return this
    }

    /** Gust factor at world ([x], [z]) and time [t], roughly in [-1, 1]; 0 without a gust scale. */
    fun gust(x: Float, z: Float, t: Float): Float {
        if (gustScale <= 0f) return 0f
        val along = (x * dirX + z * dirZ) / gustScale - t * speed / gustScale
        val across = (-x * dirZ + z * dirX) / gustScale
        val g = 0.5f * sin(TAU * along) + 0.3f * sin(TAU * 2.3f * along + 1.7f) + 0.2f * sin(TAU * 0.37f * along + 4.1f)
        return g * (0.75f + 0.25f * sin(TAU * 0.37f * across + 0.6f))
    }

    /** Wind velocity (m/s, horizontal) at world ([x], [z]) and time [t], written into [out]. */
    fun sample(x: Float, z: Float, t: Float, out: Vector3f): Vector3f {
        if (speed == 0f) return out.zero()
        val s = speed * (1f + gustStrength * gust(x, z, t))
        return out.set(dirX * s, 0f, dirZ * s)
    }

    companion object {
        const val TAU = (2.0 * PI).toFloat()

        /** Inspector presets (REN-29 §3.1), in m/s. */
        const val CALM = 0.5f
        const val BREEZE = 3f
        const val STRONG = 8f
        const val STORM = 16f
    }
}
