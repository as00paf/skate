package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import kotlin.math.abs
import kotlin.math.round

/** One key of a clip, by its track and time. Times identify keys because indices shift when keys are retimed. */
data class KeyRef(val bone: String, val path: AnimationPath, val time: Float) {
    fun matches(track: SkTrack) = track.bone == bone && track.path == path
}

/** Keys copied from a clip; times are relative to the earliest copied key. */
data class CopiedKeys(val keys: List<Pair<KeyRef, SkKey>>)

/**
 * Clip-wide key operations for the animation editor (ANI-14, ANI-12 §2.1): several tracks at once, a selection of
 * [KeyRef]s, and retiming. Pure, like [KeyEditing]; every function returns new values.
 */
object ClipEditing {
    private const val EPS = KeyEditing.TIME_EPSILON

    /** The keys of [clip] that [selection] points at, as (track index, key index) pairs. */
    fun resolve(clip: SkClip, selection: Collection<KeyRef>): Map<Int, Set<Int>> {
        val out = HashMap<Int, MutableSet<Int>>()
        for ((ti, track) in clip.tracks.withIndex()) {
            val refs = selection.filter { it.matches(track) }
            if (refs.isEmpty()) continue
            track.keys.forEachIndexed { ki, key -> if (refs.any { abs(it.time - key.time) <= EPS }) out.getOrPut(ti) { HashSet() } += ki }
        }
        return out
    }

    fun refsOf(clip: SkClip): List<KeyRef> = clip.tracks.flatMap { t -> t.keys.map { KeyRef(t.bone, t.path, it.time) } }

    /** Keys of the given tracks whose time is in [t0, t1] (either order): a box selection on the dope sheet. */
    fun keysIn(clip: SkClip, tracks: Collection<Pair<String, AnimationPath>>, t0: Float, t1: Float): Set<KeyRef> {
        val lo = minOf(t0, t1) - EPS
        val hi = maxOf(t0, t1) + EPS
        return clip.tracks.filter { (it.bone to it.path) in tracks }
            .flatMap { t -> t.keys.filter { it.time in lo..hi }.map { KeyRef(t.bone, t.path, it.time) } }.toSet()
    }

    /**
     * Moves the selected keys by [dt] seconds. The shift is clamped so the moved group stays inside
     * [0, duration] and keeps its spacing. Returns the clip and the selection at the new times.
     */
    fun moveKeys(clip: SkClip, selection: Set<KeyRef>, dt: Float): Pair<SkClip, Set<KeyRef>> {
        if (selection.isEmpty()) return clip to selection
        val minT = selection.minOf { it.time }
        val maxT = selection.maxOf { it.time }
        val d = dt.coerceIn(-minT, clip.duration - maxT)
        return retime(clip, selection) { it + d }
    }

    /** Scales the selected key times around [pivot] by [factor] (> 0), clamped to [0, duration]. */
    fun scaleKeys(clip: SkClip, selection: Set<KeyRef>, pivot: Float, factor: Float): Pair<SkClip, Set<KeyRef>> {
        require(factor > 0f) { "Scale factor must be positive" }
        return retime(clip, selection) { (pivot + (it - pivot) * factor).coerceIn(0f, clip.duration) }
    }

    /** Stretches the whole clip (keys and events) to [newDuration] seconds: slower or faster playback. */
    fun retimeClip(clip: SkClip, newDuration: Float): SkClip {
        require(newDuration > 0f) { "Duration must be positive" }
        val f = newDuration / clip.duration
        return clip.copy(
            duration = newDuration,
            tracks = clip.tracks.map { t ->
                t.copy(keys = t.keys.map { k ->
                    // Time stretch divides slopes: tangents are value per second.
                    k.copy(time = k.time * f, inTangent = k.inTangent?.map { it / f }, outTangent = k.outTangent?.map { it / f })
                })
            },
            events = clip.events.map { it.copy(time = it.time * f) },
        )
    }

    /** Sets the clip length; keys and events past the new end are dropped (trim). */
    fun setDuration(clip: SkClip, duration: Float): SkClip {
        require(duration > 0f) { "Duration must be positive" }
        return clip.copy(
            duration = duration,
            tracks = clip.tracks.map { t -> t.copy(keys = t.keys.filter { it.time <= duration + EPS }) },
            events = clip.events.filter { it.time <= duration + EPS },
        )
    }

    fun deleteKeys(clip: SkClip, selection: Set<KeyRef>): SkClip {
        val hit = resolve(clip, selection)
        return clip.copy(tracks = clip.tracks.mapIndexed { i, t -> hit[i]?.let { KeyEditing.removeKeys(t, it) } ?: t })
    }

    /**
     * Keys [bones] at [time] with the pose the clip plays there ("Key selected bones"): every existing track of the
     * bone gets a key with its sampled value, and a bone without tracks gets a rotation key at its rest pose.
     * Returns the clip and the new keys.
     */
    fun keyBones(clip: SkClip, bones: Collection<String>, time: Float): Pair<SkClip, Set<KeyRef>> {
        val t = time.coerceIn(0f, clip.duration)
        var out = clip
        val keyed = HashSet<KeyRef>()
        for (bone in bones) {
            val tracks = clip.tracks.filter { it.bone == bone }
            if (tracks.isEmpty()) {
                out = out.withTrack(KeyEditing.insertKey(SkTrack(bone, AnimationPath.ROTATION), t, floatArrayOf(0f, 0f, 0f, 1f)))
                keyed += KeyRef(bone, AnimationPath.ROTATION, t)
            }
            for (track in tracks) {
                val value = if (track.keys.isEmpty()) rest(track.path) else KeyEditing.sample(track, t)
                out = out.withTrack(KeyEditing.insertKey(track, t, value))
                keyed += KeyRef(bone, track.path, t)
            }
        }
        return out to keyed
    }

    /** Replaces the value of one key. */
    fun setKeyValue(clip: SkClip, ref: KeyRef, value: FloatArray): SkClip {
        val ti = clip.tracks.indexOfFirst { ref.matches(it) }
        require(ti >= 0) { "No track ${ref.bone}/${ref.path}" }
        val ki = clip.tracks[ti].keys.indexOfFirst { abs(it.time - ref.time) <= EPS }
        require(ki >= 0) { "No key at ${ref.time}" }
        val v = if (ref.path == AnimationPath.ROTATION) QuatMath.normalize(value.copyOf()) else value
        return clip.withTrack(KeyEditing.setValue(clip.tracks[ti], ki, v))
    }

    fun copy(clip: SkClip, selection: Set<KeyRef>): CopiedKeys {
        if (selection.isEmpty()) return CopiedKeys(emptyList())
        val start = selection.minOf { it.time }
        val hit = resolve(clip, selection)
        return CopiedKeys(hit.flatMap { (ti, keys) ->
            val t = clip.tracks[ti]
            keys.map { ki -> val k = t.keys[ki]; KeyRef(t.bone, t.path, k.time - start) to k }
        })
    }

    /**
     * Pastes [copied] with its first key at [time], onto the same bones and paths (tracks are created when missing).
     * Keys past the clip's end are dropped. Returns the clip and the pasted keys.
     */
    fun paste(clip: SkClip, copied: CopiedKeys, time: Float): Pair<SkClip, Set<KeyRef>> {
        var out = clip
        val pasted = HashSet<KeyRef>()
        for ((ref, key) in copied.keys) {
            val t = time + ref.time
            if (t < -EPS || t > clip.duration + EPS) continue
            val track = out.track(ref.bone, ref.path) ?: SkTrack(ref.bone, ref.path)
            val inserted = KeyEditing.insertKey(track, t.coerceIn(0f, clip.duration), key.value.toFloatArray(), key.tangentMode)
            val at = t.coerceIn(0f, clip.duration)
            // insertKey resets tangents; keep the copied handles.
            out = out.withTrack(inserted.copy(keys = inserted.keys.map { k ->
                if (abs(k.time - at) <= EPS) k.copy(inTangent = key.inTangent, outTangent = key.outTangent) else k
            }))
            pasted += KeyRef(ref.bone, ref.path, at)
        }
        return out to pasted
    }

    /** [time] on the clip's frame grid. */
    fun snap(time: Float, fps: Float): Float = round(time * fps) / fps

    private fun rest(path: AnimationPath) = when (path) {
        AnimationPath.ROTATION -> floatArrayOf(0f, 0f, 0f, 1f)
        AnimationPath.SCALE -> floatArrayOf(1f, 1f, 1f)
        else -> floatArrayOf(0f, 0f, 0f)
    }

    private fun retime(clip: SkClip, selection: Set<KeyRef>, newTime: (Float) -> Float): Pair<SkClip, Set<KeyRef>> {
        val hit = resolve(clip, selection)
        val moved = HashSet<KeyRef>()
        val tracks = clip.tracks.mapIndexed { ti, track ->
            val keys = hit[ti] ?: return@mapIndexed track
            val movedKeys = track.keys.filterIndexed { i, _ -> i in keys }.map { it.copy(time = newTime(it.time)) }
            // Two selected keys can land on the same time (scale towards a pivot): keep the later one.
            val unique = movedKeys.associateBy { round(it.time / EPS) }.values
            unique.forEach { moved += KeyRef(track.bone, track.path, it.time) }
            val kept = track.keys.filterIndexed { i, _ -> i !in keys }.filter { k -> unique.none { abs(it.time - k.time) <= EPS } }
            track.copy(keys = (kept + unique).sortedBy { it.time })
        }
        return clip.copy(tracks = tracks) to moved
    }
}
