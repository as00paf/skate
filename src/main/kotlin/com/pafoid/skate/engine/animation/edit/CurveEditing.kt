package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import org.joml.Vector3f

/** Tangent choices of the curve editor (ANI-12 §2.1). [STEP] is per track: the runtime sampler interpolates per track. */
enum class CurveTangent(val mode: TangentMode?) {
    AUTO(TangentMode.AUTO),
    LINEAR(TangentMode.LINEAR),
    FLAT(TangentMode.FLAT),
    FREE(TangentMode.FREE),
    BROKEN(TangentMode.BROKEN),
    STEP(null),
}

/** Which handle of a key. */
enum class HandleSide { IN, OUT }

/**
 * What the curve editor draws for one track: three scalar curves (X, Y, Z of a translation or scale, or Euler degrees
 * of a rotation). [keyValues] and [sampleValues] are `[component][index]`. Rotation curves are unwrapped together, so
 * the keys sit on the sampled curve.
 */
class TrackCurveView(
    val keyTimes: FloatArray,
    val keyValues: Array<FloatArray>,
    val sampleTimes: FloatArray,
    val sampleValues: Array<FloatArray>,
) {
    /** Lowest and highest value over the visible curves ([components]), for framing. Null without keys. */
    fun range(components: Set<Int> = setOf(0, 1, 2)): Pair<Float, Float>? {
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        for (c in components) {
            for (v in keyValues[c]) { lo = minOf(lo, v); hi = maxOf(hi, v) }
            for (v in sampleValues[c]) { lo = minOf(lo, v); hi = maxOf(hi, v) }
        }
        return if (lo > hi) null else lo to hi
    }
}

/**
 * Pure operations behind the curve editor (ANI-15, ANI-12 §2.1). Rotations are shown and edited as XYZ Euler degrees
 * but stay quaternions in the clip ([EulerCurves]); translations and scales are edited per component. Tangent handles
 * are stored as glTF cubic-spline tangents, so the runtime sampler does not change.
 */
object CurveEditing {
    const val COMPONENTS = 3

    /**
     * Curves of [track] with [samples] evenly spaced samples over [t0, t1] (plus the key times), evaluated with the
     * runtime sampler.
     */
    fun view(track: SkTrack, t0: Float, t1: Float, samples: Int): TrackCurveView {
        val keyTimes = track.times
        if (track.keys.isEmpty()) return TrackCurveView(keyTimes, Array(COMPONENTS) { FloatArray(0) }, FloatArray(0), Array(COMPONENTS) { FloatArray(0) })
        val n = samples.coerceAtLeast(2)
        val grid = FloatArray(n) { t0 + (t1 - t0) * it / (n - 1) }
        val times = (grid.asList() + keyTimes.asList()).sorted().fold(ArrayList<Float>()) { acc, t ->
            if (acc.isEmpty() || t - acc.last() > KeyEditing.TIME_EPSILON) acc.add(t)
            acc
        }.toFloatArray()
        val sampler = SkAnimFormat.toSampler(track)
        val values: Array<FloatArray> = if (track.path == AnimationPath.ROTATION) {
            // Sample the quaternions, then unwrap them as one Euler curve so keys and samples agree.
            val sampled = SkTrack(track.bone, track.path, InterpolationType.LINEAR, times.map { SkKey(it, KeyEditing.sample(sampler, track.path, it).toList()) })
            val euler = EulerCurves.fromTrack(sampled)
            arrayOf(euler.x, euler.y, euler.z)
        } else {
            val rows = times.map { KeyEditing.sample(sampler, track.path, it) }
            Array(COMPONENTS) { c -> FloatArray(times.size) { rows[it][c] } }
        }
        val keyIndex = IntArray(keyTimes.size) { k -> nearest(times, keyTimes[k]) }
        return TrackCurveView(
            keyTimes = keyTimes,
            keyValues = Array(COMPONENTS) { c -> FloatArray(keyTimes.size) { values[c][keyIndex[it]] } },
            sampleTimes = times,
            sampleValues = values,
        )
    }

    /**
     * Adds [delta] to [component] of the keys at [indices] (Euler degrees for rotations, written back as quaternions).
     * Rotation keys with stored tangents fall back to AUTO ([EulerCurves.toTrack]).
     */
    fun offsetKeys(track: SkTrack, indices: Set<Int>, component: Int, delta: Float): SkTrack {
        require(component in 0 until COMPONENTS) { "Component $component out of range" }
        if (indices.isEmpty() || delta == 0f) return track
        if (track.path == AnimationPath.ROTATION) {
            var euler = EulerCurves.fromTrack(track)
            for (i in indices) {
                val v = euler.at(i)
                when (component) { 0 -> v.x += delta; 1 -> v.y += delta; else -> v.z += delta }
                euler = euler.withKey(i, v)
            }
            return euler.toTrack(track)
        }
        return track.copy(keys = track.keys.mapIndexed { i, k ->
            if (i !in indices) k else k.copy(value = k.value.toMutableList().also { it[component] += delta })
        })
    }

    /**
     * Applies [tangent] to the keys at [indices]. [CurveTangent.STEP] switches the whole track to stepped playback;
     * any other mode makes the track a cubic spline. A linear track keeps its shape on the other keys (they become
     * LINEAR keys). FREE and BROKEN keep the current tangents as their stored handles.
     */
    fun setTangent(track: SkTrack, indices: Set<Int>, tangent: CurveTangent): SkTrack {
        if (indices.isEmpty()) return track
        val mode = tangent.mode ?: return track.copy(interpolation = InterpolationType.STEP)
        var t = toCubic(track)
        for (i in indices.sorted()) if (i in t.keys.indices) t = KeyEditing.setTangents(t, i, mode)
        return t
    }

    /**
     * Sets one handle of key [index] to [slope] (value per second) on [component]. Translations and scales only:
     * rotation handles cannot be expressed per Euler axis. A key that is not BROKEN becomes FREE and both of its
     * handles move together (a smooth key); a BROKEN key moves only [side].
     */
    fun setHandle(track: SkTrack, index: Int, component: Int, side: HandleSide, slope: Float): SkTrack {
        require(track.path != AnimationPath.ROTATION) { "Rotation tangents are not editable per axis" }
        require(component in 0 until COMPONENTS) { "Component $component out of range" }
        val cubic = toCubic(track)
        val current = Tangents.resolve(cubic)[index]
        val broken = cubic.keys[index].tangentMode == TangentMode.BROKEN
        val inT = current.inTangent.copyOf()
        val outT = current.outTangent.copyOf()
        if (!broken || side == HandleSide.IN) inT[component] = slope
        if (!broken || side == HandleSide.OUT) outT[component] = slope
        return KeyEditing.setTangents(cubic, index, if (broken) TangentMode.BROKEN else TangentMode.FREE, inT, outT)
    }

    /** Resolved handles of key [index] (value per second), or null when the track is not a cubic spline. */
    fun handles(track: SkTrack, index: Int): KeyTangents? =
        if (track.interpolation != InterpolationType.CUBIC_SPLINE || index !in track.keys.indices) null
        else Tangents.resolve(track)[index]

    /** The tangent the curve editor shows as current for the keys at [indices] (null when they differ). */
    fun currentTangent(track: SkTrack, indices: Set<Int>): CurveTangent? {
        if (track.interpolation == InterpolationType.STEP) return CurveTangent.STEP
        if (track.interpolation == InterpolationType.LINEAR) return CurveTangent.LINEAR
        val modes = indices.mapNotNull { track.keys.getOrNull(it)?.tangentMode }.toSet()
        val mode = modes.singleOrNull() ?: return null
        return CurveTangent.entries.first { it.mode == mode }
    }

    /** Euler degrees of a rotation key value, or the value itself for translations and scales. */
    fun displayValue(path: AnimationPath, value: List<Float>): Vector3f =
        if (path == AnimationPath.ROTATION) EulerCurves.toEulerDeg(QuatMath.toQuat(value.toFloatArray()))
        else Vector3f(value[0], value[1], value[2])

    /**
     * [track] as a cubic spline. A linear track gets LINEAR keys so it keeps its shape (exactly for translations and
     * scales; rotations go from slerp to a normalized lerp, which is within a fraction of a degree between mocap keys).
     */
    private fun toCubic(track: SkTrack): SkTrack = when (track.interpolation) {
        InterpolationType.CUBIC_SPLINE -> track
        InterpolationType.LINEAR -> track.copy(
            interpolation = InterpolationType.CUBIC_SPLINE,
            keys = track.keys.map { it.copy(tangentMode = TangentMode.LINEAR, inTangent = null, outTangent = null) },
        )
        else -> track.copy(interpolation = InterpolationType.CUBIC_SPLINE)
    }

    private fun nearest(sorted: FloatArray, t: Float): Int {
        val i = sorted.asList().binarySearch(t).let { if (it >= 0) it else (-it - 1) }
        return when {
            i <= 0 -> 0
            i >= sorted.size -> sorted.size - 1
            t - sorted[i - 1] <= sorted[i] - t -> i - 1
            else -> i
        }
    }
}
