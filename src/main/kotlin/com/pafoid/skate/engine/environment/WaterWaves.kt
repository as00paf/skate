package com.pafoid.skate.engine.environment

import kotlinx.serialization.Serializable
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One Gerstner wave (REN-29 §3.4): [directionDeg] it travels towards (0° = +Z, 90° = +X), [wavelength] in metres,
 * [amplitude] in metres and [steepness] 0..1 (crest sharpness). Speed follows deep-water dispersion, so only the
 * shape is authored.
 */
@Serializable
data class GerstnerWave(
    var directionDeg: Float = 0f,
    var wavelength: Float = 10f,
    var amplitude: Float = 0.1f,
    var steepness: Float = 0.5f,
)

/**
 * Sum of up to [MAX_WAVES] Gerstner waves (REN-29 §6.1), pure so rendering (`water_waves.glsl` mirrors it, the table
 * test pins both) and buoyancy (PHY-16) see the same surface at the same clock time.
 *
 * Wave i at grid point p and time t has phase `θ = k (d · p) − ω t` with `k = 2π / λ`, `ω = sqrt(g k)`; it moves the
 * point by `(Q A d.x cos θ, A sin θ, Q A d.z cos θ)`. Steepness is scaled so `Σ Q k A ≤ 1`: crests never loop.
 */
class WaterWaves {
    var count = 0
        private set
    val dirX = FloatArray(MAX_WAVES)
    val dirZ = FloatArray(MAX_WAVES)
    val k = FloatArray(MAX_WAVES)
    val amplitude = FloatArray(MAX_WAVES)
    /** Normalized steepness Q per wave (horizontal displacement is Q × amplitude). */
    val q = FloatArray(MAX_WAVES)
    val omega = FloatArray(MAX_WAVES)

    /** Sum of the amplitudes: the most the surface rises above (or falls below) its rest height. */
    val totalAmplitude: Float get() = (0 until count).sumOf { amplitude[it].toDouble() }.toFloat()

    /**
     * Loads [waves] (first [MAX_WAVES]). With [windDriven], amplitudes scale by `windSpeed / 3 m/s` (clamped 0..3) and
     * wave 0 turns to [windDirectionDeg].
     */
    fun configure(
        waves: List<GerstnerWave>,
        windDriven: Boolean = false,
        windSpeed: Float = 0f,
        windDirectionDeg: Float = 0f,
    ): WaterWaves {
        count = 0
        val scale = if (windDriven) (windSpeed / WIND_REFERENCE).coerceIn(0f, MAX_WIND_SCALE) else 1f
        var qkaSum = 0f
        for (wave in waves) {
            if (count == MAX_WAVES) break
            if (wave.wavelength <= 0f) continue
            val i = count++
            val deg = if (windDriven && i == 0) windDirectionDeg else wave.directionDeg
            val rad = Math.toRadians(deg.toDouble())
            dirX[i] = sin(rad).toFloat()
            dirZ[i] = cos(rad).toFloat()
            k[i] = TAU / wave.wavelength
            amplitude[i] = max(0f, wave.amplitude * scale)
            q[i] = wave.steepness.coerceIn(0f, 1f)
            omega[i] = sqrt(GRAVITY * k[i])
            qkaSum += q[i] * k[i] * amplitude[i]
        }
        if (qkaSum > 1f) for (i in 0 until count) q[i] /= qkaSum
        return this
    }

    /** Sum of `Q k A` after normalization (≤ 1). */
    fun steepnessSum(): Float = (0 until count).sumOf { (q[it] * k[it] * amplitude[it]).toDouble() }.toFloat()

    private fun phase(i: Int, x: Float, z: Float, t: Float) = k[i] * (dirX[i] * x + dirZ[i] * z) - omega[i] * t

    /** Displaced surface point of grid point ([x], [z]) at time [t], relative to the rest plane (y = 0). */
    fun displace(x: Float, z: Float, t: Float, out: Vector3f): Vector3f {
        var dx = 0f; var dy = 0f; var dz = 0f
        for (i in 0 until count) {
            val th = phase(i, x, z, t)
            val c = cos(th); val s = sin(th)
            val qa = q[i] * amplitude[i]
            dx += qa * dirX[i] * c
            dz += qa * dirZ[i] * c
            dy += amplitude[i] * s
        }
        return out.set(x + dx, dy, z + dz)
    }

    /** Unit surface normal at grid point ([x], [z]) and time [t]. */
    fun normalAt(x: Float, z: Float, t: Float, out: Vector3f): Vector3f {
        var nx = 0f; var ny = 1f; var nz = 0f
        for (i in 0 until count) {
            val th = phase(i, x, z, t)
            val c = cos(th); val s = sin(th)
            val ka = k[i] * amplitude[i]
            nx -= dirX[i] * ka * c
            nz -= dirZ[i] * ka * c
            ny -= q[i] * ka * s
        }
        return out.set(nx, ny, nz).normalize()
    }

    /**
     * Determinant of the horizontal Jacobian at grid point ([x], [z]): 1 on flat water, falling towards 0 where crests
     * pinch (crest foam, REN-29 §6.4).
     */
    fun jacobian(x: Float, z: Float, t: Float): Float {
        var jxx = 1f; var jzz = 1f; var jxz = 0f
        for (i in 0 until count) {
            val s = sin(phase(i, x, z, t))
            val qka = q[i] * k[i] * amplitude[i]
            jxx -= qka * dirX[i] * dirX[i] * s
            jzz -= qka * dirZ[i] * dirZ[i] * s
            jxz -= qka * dirX[i] * dirZ[i] * s
        }
        return jxx * jzz - jxz * jxz
    }

    /**
     * Surface height at world ([x], [z]) and time [t]. Gerstner waves move points sideways, so this first finds the grid
     * point that lands on ([x], [z]) with [HEIGHT_ITERATIONS] fixed-point steps; exact enough for buoyancy.
     */
    fun heightAt(x: Float, z: Float, t: Float): Float {
        var px = x; var pz = z
        repeat(HEIGHT_ITERATIONS) {
            var dx = 0f; var dz = 0f
            for (i in 0 until count) {
                val c = cos(phase(i, px, pz, t))
                val qa = q[i] * amplitude[i]
                dx += qa * dirX[i] * c
                dz += qa * dirZ[i] * c
            }
            px = x - dx
            pz = z - dz
        }
        var y = 0f
        for (i in 0 until count) y += amplitude[i] * sin(phase(i, px, pz, t))
        return y
    }

    companion object {
        const val MAX_WAVES = 4
        const val GRAVITY = 9.81f
        const val TAU = (2.0 * PI).toFloat()
        const val HEIGHT_ITERATIONS = 3
        /** Wind speed at which wind-driven waves have their authored amplitude (a breeze). */
        const val WIND_REFERENCE = 3f
        const val MAX_WIND_SCALE = 3f
    }
}
