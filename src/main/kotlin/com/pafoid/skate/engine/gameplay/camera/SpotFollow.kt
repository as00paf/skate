package com.pafoid.skate.engine.gameplay.camera

import org.joml.Vector3f
import kotlin.math.exp

/**
 * A filmer's camera (GPL-23 §7): it stands at the spot nearest the target, then follows the target from that side,
 * staying between a minimum and a maximum horizontal distance at the spot's height, and always aims at it. Pure math.
 */
object SpotFollow {
    /** Index of the spot horizontally nearest [target], or -1 when there is none. */
    fun nearest(spots: List<Vector3f>, target: Vector3f): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        spots.forEachIndexed { i, spot ->
            val dx = spot.x - target.x
            val dz = spot.z - target.z
            val d = dx * dx + dz * dz
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    /**
     * Where the camera wants to be: on the horizontal line from [target] toward [spot], at a distance clamped to
     * [minDistance]..[maxDistance], at the spot's height. A target right on the spot keeps the camera on the spot.
     */
    fun desired(spot: Vector3f, target: Vector3f, minDistance: Float, maxDistance: Float, out: Vector3f = Vector3f()): Vector3f {
        val dx = spot.x - target.x
        val dz = spot.z - target.z
        val distance = kotlin.math.sqrt(dx * dx + dz * dz)
        if (distance < 1e-4f) return out.set(spot)
        val clamped = distance.coerceIn(minDistance, maxOf(minDistance, maxDistance))
        val k = clamped / distance
        return out.set(target.x + dx * k, spot.y, target.z + dz * k)
    }

    /** Frame-rate independent ease of [current] toward [goal]; [sharpness] in 1/s (0: no movement). */
    fun ease(current: Vector3f, goal: Vector3f, sharpness: Float, dt: Float): Vector3f =
        current.lerp(goal, 1f - exp(-sharpness * dt))
}
