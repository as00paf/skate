package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import kotlinx.serialization.Serializable

/**
 * Editable animation clip, stored as `*.skanim` JSON (ANI-12 §2.2, ANI-13).
 *
 * Values are in the same channel space as the runtime `Animation` (what `AnimationLoader` produces), so an
 * imported clip converts to a `SkClip` and back without changing how it plays. Times are seconds, translations
 * metres, rotations unit quaternions `[x, y, z, w]`. Tangents are glTF cubic-spline tangents (value per second).
 */
@Serializable
data class SkClip(
    val version: Int = FORMAT_VERSION,
    val name: String,
    /** Editing frame rate: snapping, resampling and the timeline grid. Playback is time based. */
    val fps: Float = 30f,
    /** Clip length in seconds; keys never go past it. */
    val duration: Float,
    val loop: Boolean = true,
    /** Hint for tools: the clip targets a humanoid rig (see `HumanoidBone`). */
    val humanoid: Boolean = false,
    val tracks: List<SkTrack> = emptyList(),
    val events: List<SkEvent> = emptyList(),
) {
    fun track(bone: String, path: AnimationPath): SkTrack? = tracks.firstOrNull { it.bone == bone && it.path == path }

    /** Returns a copy with [track] added, or replacing the track with the same bone and path. */
    fun withTrack(track: SkTrack): SkClip {
        val i = tracks.indexOfFirst { it.bone == track.bone && it.path == track.path }
        return copy(tracks = if (i < 0) tracks + track else tracks.toMutableList().also { it[i] = track })
    }

    fun mapTracks(transform: (SkTrack) -> SkTrack): SkClip = copy(tracks = tracks.map(transform))

    companion object {
        const val FORMAT_VERSION = 1
        const val EXTENSION = "skanim"
    }
}

/** One animated property of one bone. Keys are kept sorted by time. */
@Serializable
data class SkTrack(
    val bone: String,
    val path: AnimationPath,
    val interpolation: InterpolationType = InterpolationType.LINEAR,
    val keys: List<SkKey> = emptyList(),
) {
    /** Floats per value: 4 for rotations (quaternion), 3 for translation and scale. */
    val components: Int get() = componentsOf(path)

    val times: FloatArray get() = FloatArray(keys.size) { keys[it].time }

    companion object {
        fun componentsOf(path: AnimationPath) = if (path == AnimationPath.ROTATION) 4 else 3
    }
}

/** How a key's tangents are derived for [InterpolationType.CUBIC_SPLINE] tracks. */
@Serializable
enum class TangentMode {
    /** Smooth (Catmull-Rom), flattened at local extrema so curves do not overshoot. */
    AUTO,
    /** Zero slope: the curve eases in and out of the key. */
    FLAT,
    /** Slopes of the straight lines to the neighbouring keys. */
    LINEAR,
    /** Uses the stored [SkKey.inTangent] / [SkKey.outTangent] (imported curves, hand-edited handles). */
    FREE,
}

@Serializable
data class SkKey(
    val time: Float,
    val value: List<Float>,
    val tangentMode: TangentMode = TangentMode.AUTO,
    val inTangent: List<Float>? = null,
    val outTangent: List<Float>? = null,
)

/** Named marker at [time] seconds (footsteps, sounds, gameplay hooks). */
@Serializable
data class SkEvent(val time: Float, val name: String)
