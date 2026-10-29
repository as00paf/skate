package com.pafoid.skate.engine.render.lighting

import org.joml.Vector3f
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.sqrt

enum class LightType(val code: Float) { POINT(0f), SPOT(1f) }

/** One point/spot light, already scaled by intensity. Cone cosines are for spot lights only. */
class LightData(
    val type: LightType,
    val position: Vector3f,
    val direction: Vector3f,
    val color: Vector3f,
    val constant: Float, val linear: Float, val quadratic: Float,
    val cosInner: Float = 0f, val cosOuter: Float = 0f,
) {
    /** Distance at which the light contributes less than 1/256 of its colour; used for culling and as the sphere of influence. */
    val range: Float = LightRange.of(max(color.x, max(color.y, color.z)), constant, linear, quadratic)
}

object LightRange {
    private const val CUTOFF = 256f
    private const val MAX_RANGE = 1000f

    /** Solves `peak / (c + l*d + q*d^2) = 1/256` for d. */
    fun of(peak: Float, c: Float, l: Float, q: Float): Float {
        val k = c - peak * CUTOFF
        if (k >= 0f) return 0f
        val d = when {
            q > 1e-6f -> (-l + sqrt(l * l - 4f * q * k)) / (2f * q)
            l > 1e-6f -> -k / l
            else -> MAX_RANGE
        }
        return d.coerceIn(0f, MAX_RANGE)
    }
}

/** std140 packing of [LightData] into the `LightBlock` array: 4 vec4 per light (64 bytes). */
object LightPacking {
    const val MAX_LIGHTS = 64
    const val FLOATS_PER_LIGHT = 16
    const val BYTES_PER_LIGHT = FLOATS_PER_LIGHT * 4

    /** Writes up to [MAX_LIGHTS] lights from the buffer's current position; returns how many were written. */
    fun pack(lights: List<LightData>, dest: FloatBuffer): Int {
        val n = minOf(lights.size, MAX_LIGHTS)
        for (i in 0 until n) {
            val l = lights[i]
            dest.put(l.position.x).put(l.position.y).put(l.position.z).put(l.range)
            dest.put(l.color.x).put(l.color.y).put(l.color.z).put(l.type.code)
            dest.put(l.direction.x).put(l.direction.y).put(l.direction.z).put(l.cosInner)
            dest.put(l.constant).put(l.linear).put(l.quadratic).put(l.cosOuter)
        }
        return n
    }
}
