package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath

/** Maximum error allowed when removing keys. */
data class ReductionTolerance(
    val translationMetres: Float = 0.001f,
    val rotationDeg: Float = 0.25f,
    val scale: Float = 0.001f,
) {
    fun of(path: AnimationPath) = when (path) {
        AnimationPath.TRANSLATION -> translationMetres
        AnimationPath.ROTATION -> rotationDeg
        AnimationPath.SCALE -> scale
    }
}

/**
 * Keyframe reduction for dense clips (mocap, baked curves). Greedy refinement: start from the end keys and add back
 * the worst-fitting original key until every original key is reproduced within the tolerance. The fit is checked with
 * the real runtime sampler, so it holds for STEP, LINEAR and CUBIC_SPLINE tracks.
 */
object Reduction {
    fun reduce(clip: SkClip, tolerance: ReductionTolerance = ReductionTolerance()): SkClip =
        clip.mapTracks { reduce(it, tolerance.of(it.path)) }

    fun reduce(track: SkTrack, tolerance: Float): SkTrack {
        val n = track.keys.size
        if (n <= 2) return track
        val values = Tangents.values(track)
        if (values.all { error(track.path, it, values[0]) <= tolerance }) return track.copy(keys = listOf(track.keys[0]))

        val kept = sortedSetOf(0, n - 1)
        while (true) {
            val candidate = track.copy(keys = kept.map { track.keys[it] })
            val sampler = SkAnimFormat.toSampler(candidate)
            var worst = -1
            var worstError = tolerance
            for (i in 0 until n) {
                if (i in kept) continue
                val e = error(track.path, KeyEditing.sample(sampler, track.path, track.keys[i].time), values[i])
                if (e > worstError) { worstError = e; worst = i }
            }
            if (worst < 0) return candidate
            kept.add(worst)
        }
    }

    internal fun error(path: AnimationPath, a: FloatArray, b: FloatArray): Float =
        if (path == AnimationPath.ROTATION) QuatMath.angleDeg(a, b)
        else kotlin.math.sqrt((0 until 3).sumOf { ((a[it] - b[it]) * (a[it] - b[it])).toDouble() }).toFloat()
}
