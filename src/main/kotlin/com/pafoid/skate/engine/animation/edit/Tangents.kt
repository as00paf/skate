package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath

/** Resolved tangents of one key, in value units per second. */
class KeyTangents(val inTangent: FloatArray, val outTangent: FloatArray)

/** Derives cubic-spline tangents from each key's [TangentMode]. Pure. */
object Tangents {

    /** Tangents for every key of [track]. Rotation values are made hemisphere-continuous first. */
    fun resolve(track: SkTrack): List<KeyTangents> {
        val n = track.keys.size
        val c = track.components
        val values = values(track)
        val times = track.times
        return List(n) { i ->
            val key = track.keys[i]
            when (key.tangentMode) {
                TangentMode.FLAT -> KeyTangents(FloatArray(c), FloatArray(c))
                TangentMode.LINEAR -> {
                    val incoming = if (i > 0) slope(values, times, i - 1, i, c) else null
                    val outgoing = if (i < n - 1) slope(values, times, i, i + 1, c) else null
                    KeyTangents(incoming ?: outgoing ?: FloatArray(c), outgoing ?: incoming ?: FloatArray(c))
                }
                TangentMode.FREE -> {
                    val fallback by lazy { auto(values, times, i, c) }
                    KeyTangents(
                        key.inTangent?.takeIf { it.size == c }?.toFloatArray() ?: fallback,
                        key.outTangent?.takeIf { it.size == c }?.toFloatArray() ?: fallback,
                    )
                }
                TangentMode.AUTO -> auto(values, times, i, c).let { KeyTangents(it, it.copyOf()) }
            }
        }
    }

    /** Key values as rows; rotations are flipped where needed so neighbouring quaternions share a hemisphere. */
    fun values(track: SkTrack): Array<FloatArray> {
        val rows = Array(track.keys.size) { track.keys[it].value.toFloatArray() }
        if (track.path == AnimationPath.ROTATION) QuatMath.makeContinuous(rows)
        return rows
    }

    private fun slope(v: Array<FloatArray>, t: FloatArray, a: Int, b: Int, c: Int): FloatArray {
        val dt = t[b] - t[a]
        return FloatArray(c) { if (dt <= 0f) 0f else (v[b][it] - v[a][it]) / dt }
    }

    /** Non-uniform Catmull-Rom, one-sided at the ends, zero at local extrema (no overshoot). */
    private fun auto(v: Array<FloatArray>, t: FloatArray, i: Int, c: Int): FloatArray {
        val n = v.size
        if (n < 2) return FloatArray(c)
        if (i == 0) return slope(v, t, 0, 1, c)
        if (i == n - 1) return slope(v, t, n - 2, n - 1, c)
        val dt = t[i + 1] - t[i - 1]
        return FloatArray(c) { k ->
            val before = v[i][k] - v[i - 1][k]
            val after = v[i + 1][k] - v[i][k]
            if (before * after <= 0f || dt <= 0f) 0f else (v[i + 1][k] - v[i - 1][k]) / dt
        }
    }
}
