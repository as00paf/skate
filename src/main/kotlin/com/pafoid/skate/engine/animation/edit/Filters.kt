package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Curve cleanup for mocap and baked clips. Filters change key values only (times are kept); rotations are filtered
 * component-wise on hemisphere-aligned quaternions and renormalised.
 */
object Filters {

    /** Samples [track] every `1/fps` seconds from 0 to [duration] (inclusive). Resampled keys use AUTO tangents. */
    fun resample(track: SkTrack, fps: Float, duration: Float): SkTrack {
        require(fps > 0f && duration >= 0f) { "Resample needs fps > 0 and duration >= 0" }
        if (track.keys.isEmpty()) return track
        val sampler = SkAnimFormat.toSampler(track)
        val frames = (duration * fps).roundToInt()
        val keys = (0..frames).map { f ->
            val t = (f / fps).coerceAtMost(duration)
            SkKey(t, KeyEditing.sample(sampler, track.path, t).toList())
        }.distinctBy { it.time }
        return track.copy(keys = keys)
    }

    fun resample(clip: SkClip, fps: Float = clip.fps): SkClip = clip.mapTracks { resample(it, fps, clip.duration) }.copy(fps = fps)

    /** Gaussian smoothing with standard deviation [sigmaSeconds]; works on uneven key spacing. */
    fun gaussian(track: SkTrack, sigmaSeconds: Float): SkTrack {
        if (sigmaSeconds <= 0f || track.keys.size < 3) return track
        val values = Tangents.values(track)
        val times = track.times
        val c = track.components
        val twoSigma2 = 2f * sigmaSeconds * sigmaSeconds
        val out = Array(values.size) { i ->
            val acc = FloatArray(c)
            var wSum = 0f
            for (j in values.indices) {
                val d = times[j] - times[i]
                if (abs(d) > 3f * sigmaSeconds) continue
                val w = exp(-(d * d) / twoSigma2)
                for (k in 0 until c) acc[k] += values[j][k] * w
                wSum += w
            }
            for (k in 0 until c) acc[k] /= wSum
            acc
        }
        return withValues(track, out)
    }

    /**
     * One-Euro filter (Casiez et al. 2012): strong smoothing when the value moves slowly, little lag when it moves fast.
     * [minCutoffHz] lowers jitter, [beta] lowers lag on fast motion.
     */
    fun oneEuro(track: SkTrack, minCutoffHz: Float = 1f, beta: Float = 0f, derivativeCutoffHz: Float = 1f): SkTrack {
        if (track.keys.size < 2) return track
        val values = Tangents.values(track)
        val times = track.times
        val c = track.components
        val out = Array(values.size) { values[it].copyOf() }
        val dx = FloatArray(c)
        for (i in 1 until values.size) {
            val dt = times[i] - times[i - 1]
            if (dt <= 0f) { out[i] = out[i - 1].copyOf(); continue }
            for (k in 0 until c) {
                val rawDx = (values[i][k] - out[i - 1][k]) / dt
                dx[k] += alpha(derivativeCutoffHz, dt) * (rawDx - dx[k])
                val cutoff = minCutoffHz + beta * abs(dx[k])
                out[i][k] = out[i - 1][k] + alpha(cutoff, dt) * (values[i][k] - out[i - 1][k])
            }
        }
        return withValues(track, out)
    }

    private fun alpha(cutoffHz: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoffHz)
        return 1f / (1f + tau / dt)
    }

    private fun withValues(track: SkTrack, rows: Array<FloatArray>): SkTrack {
        if (track.path == AnimationPath.ROTATION) rows.forEach { QuatMath.normalize(it) }
        return track.copy(keys = track.keys.mapIndexed { i, k -> k.copy(value = rows[i].toList()) })
    }
}
