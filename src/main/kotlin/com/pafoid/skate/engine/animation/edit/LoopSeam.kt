package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import org.joml.Quaternionf
import kotlin.math.abs

/**
 * Makes a clip loop without a pop: over the last [blendSeconds] each track is offset progressively (smoothstep) so its
 * value at the clip end equals its value at the start. Keys before the blend window are untouched.
 */
object LoopSeam {
    fun apply(clip: SkClip, blendSeconds: Float, paths: Set<AnimationPath> = AnimationPath.entries.toSet()): SkClip {
        require(blendSeconds > 0f) { "Loop seam blend must be positive" }
        val window = blendSeconds.coerceAtMost(clip.duration)
        return clip.mapTracks { if (it.path in paths) apply(it, clip.duration, window) else it }
    }

    fun apply(track: SkTrack, duration: Float, window: Float): SkTrack {
        if (track.keys.size < 2) return track
        val start = duration - window
        // Anchor keys so the curve keeps its shape at the window start and reaches the clip end.
        var t = track
        for (time in listOf(start, duration)) {
            if (t.keys.none { abs(it.time - time) <= KeyEditing.TIME_EPSILON }) t = KeyEditing.insertKey(t, time, KeyEditing.sample(t, time))
        }
        val first = KeyEditing.sample(t, 0f)
        val last = KeyEditing.sample(t, duration)
        val keys = t.keys.map { k ->
            if (k.time <= start + KeyEditing.TIME_EPSILON) return@map k
            val s = smoothstep(((k.time - start) / window).coerceIn(0f, 1f))
            k.copy(value = offset(track.path, k.value.toFloatArray(), first, last, s).toList())
        }
        return t.copy(keys = keys)
    }

    private fun offset(path: AnimationPath, v: FloatArray, first: FloatArray, last: FloatArray, s: Float): FloatArray {
        if (path != AnimationPath.ROTATION) return FloatArray(3) { v[it] + (first[it] - last[it]) * s }
        val qFirst = QuatMath.toQuat(first)
        val qLast = QuatMath.toQuat(last)
        if (qFirst.dot(qLast) < 0f) qFirst.set(-qFirst.x, -qFirst.y, -qFirst.z, -qFirst.w)
        // delta * last == first; apply a growing share of it.
        val delta = Quaternionf(qFirst).mul(Quaternionf(qLast).invert())
        val partial = Quaternionf().slerp(delta, s)
        return QuatMath.toRow(partial.mul(QuatMath.toQuat(v)).normalize())
    }

    private fun smoothstep(x: Float) = x * x * (3f - 2f * x)
}
