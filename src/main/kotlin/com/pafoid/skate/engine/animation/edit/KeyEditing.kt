package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.AnimationSampler
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs

/** Pure key operations. Every function returns a new track or clip; inputs are never mutated. */
object KeyEditing {
    /** Two keys closer than this (seconds) are the same key. */
    const val TIME_EPSILON = 1e-4f

    /** Inserts a key at [time], replacing an existing key at the same time. */
    fun insertKey(track: SkTrack, time: Float, value: FloatArray, mode: TangentMode = TangentMode.AUTO): SkTrack {
        require(value.size == track.components) { "${track.path} keys need ${track.components} values, got ${value.size}" }
        val v = if (track.path == AnimationPath.ROTATION) QuatMath.normalize(value.copyOf()) else value
        val key = SkKey(time.coerceAtLeast(0f), v.toList(), mode)
        val kept = track.keys.filter { abs(it.time - key.time) > TIME_EPSILON }
        return track.copy(keys = (kept + key).sortedBy { it.time })
    }

    fun removeKeys(track: SkTrack, indices: Set<Int>): SkTrack =
        track.copy(keys = track.keys.filterIndexed { i, _ -> i !in indices })

    /** Shifts the selected keys by [dt] seconds (clamped at 0). Moved keys replace unselected keys they land on. */
    fun moveKeys(track: SkTrack, indices: Set<Int>, dt: Float): SkTrack =
        retime(track, indices) { (it + dt).coerceAtLeast(0f) }

    /** Scales the selected key times around [pivot] by [factor] (> 0). */
    fun scaleKeys(track: SkTrack, indices: Set<Int>, pivot: Float, factor: Float): SkTrack {
        require(factor > 0f) { "Scale factor must be positive" }
        return retime(track, indices) { (pivot + (it - pivot) * factor).coerceAtLeast(0f) }
    }

    fun setValue(track: SkTrack, index: Int, value: FloatArray): SkTrack {
        require(value.size == track.components) { "${track.path} keys need ${track.components} values" }
        return track.copy(keys = track.keys.toMutableList().also { it[index] = it[index].copy(value = value.toList()) })
    }

    /** Sets the tangent mode; for [TangentMode.FREE] the given tangents are stored (defaults to the current ones). */
    fun setTangents(track: SkTrack, index: Int, mode: TangentMode, inTangent: FloatArray? = null, outTangent: FloatArray? = null): SkTrack {
        val current = if (mode == TangentMode.FREE) Tangents.resolve(track)[index] else null
        val key = track.keys[index].copy(
            tangentMode = mode,
            inTangent = if (mode == TangentMode.FREE) (inTangent ?: current!!.inTangent).toList() else null,
            outTangent = if (mode == TangentMode.FREE) (outTangent ?: current!!.outTangent).toList() else null,
        )
        return track.copy(keys = track.keys.toMutableList().also { it[index] = key })
    }

    /** Value of [track] at [time], exactly as the runtime sampler plays it. */
    fun sample(track: SkTrack, time: Float): FloatArray = sample(SkAnimFormat.toSampler(track), track.path, time)

    internal fun sample(sampler: AnimationSampler, path: AnimationPath, time: Float): FloatArray =
        if (path == AnimationPath.ROTATION) QuatMath.toRow(Quaternionf().also { sampler.sampleQuaternionf(time, it) })
        else Vector3f().also { sampler.sampleVector3f(time, it) }.let { floatArrayOf(it.x, it.y, it.z) }

    /** Adds an event, keeping events sorted by time. */
    fun addEvent(clip: SkClip, event: SkEvent): SkClip =
        clip.copy(events = (clip.events + event.copy(time = event.time.coerceIn(0f, clip.duration))).sortedBy { it.time })

    fun removeEvent(clip: SkClip, index: Int): SkClip = clip.copy(events = clip.events.filterIndexed { i, _ -> i != index })

    fun moveEvent(clip: SkClip, index: Int, time: Float): SkClip {
        val moved = clip.events[index].copy(time = time.coerceIn(0f, clip.duration))
        return clip.copy(events = clip.events.toMutableList().also { it[index] = moved }.sortedBy { it.time })
    }

    /** Sets the interpolation of a track (STEP, LINEAR or CUBIC_SPLINE); keys keep their tangent modes. */
    fun setInterpolation(track: SkTrack, interpolation: InterpolationType) = track.copy(interpolation = interpolation)

    private fun retime(track: SkTrack, indices: Set<Int>, newTime: (Float) -> Float): SkTrack {
        val moved = track.keys.filterIndexed { i, _ -> i in indices }.map { it.copy(time = newTime(it.time)) }
        val kept = track.keys.filterIndexed { i, _ -> i !in indices }
            .filter { k -> moved.none { abs(it.time - k.time) <= TIME_EPSILON } }
        return track.copy(keys = (kept + moved).sortedBy { it.time })
    }
}
