package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.AnimationChannel
import com.pafoid.skate.engine.assets.data.models.animations.AnimationSampler
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** `*.skanim` reading/writing and conversion to and from the runtime [Animation]. Pure: no files, no GL. */
object SkAnimFormat {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun isSkAnim(path: String) = path.substringAfterLast('.', "").equals(SkClip.EXTENSION, ignoreCase = true)

    fun encode(clip: SkClip): String {
        validate(clip)
        return json.encodeToString(SkClip.serializer(), clip)
    }

    /** Parses and validates a `*.skanim` document; throws [IllegalArgumentException] with the reason when invalid. */
    fun decode(text: String): SkClip {
        val clip = try {
            json.decodeFromString(SkClip.serializer(), text)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalArgumentException("Invalid .skanim: ${e.message}", e)
        }
        validate(clip)
        return clip
    }

    fun validate(clip: SkClip) {
        require(clip.version == SkClip.FORMAT_VERSION) { "Unsupported .skanim version ${clip.version} (expected ${SkClip.FORMAT_VERSION})" }
        require(clip.duration > 0f) { "Clip '${clip.name}' needs a positive duration" }
        require(clip.fps > 0f) { "Clip '${clip.name}' needs a positive fps" }
        for (t in clip.tracks) {
            val where = "${t.bone}/${t.path}"
            val c = t.components
            for ((i, k) in t.keys.withIndex()) {
                require(k.value.size == c) { "$where key $i has ${k.value.size} values, expected $c" }
                require(k.inTangent == null || k.inTangent.size == c) { "$where key $i in-tangent size" }
                require(k.outTangent == null || k.outTangent.size == c) { "$where key $i out-tangent size" }
                require(k.time >= 0f) { "$where key $i has a negative time" }
                if (i > 0) require(k.time > t.keys[i - 1].time) { "$where keys must be strictly increasing in time" }
            }
        }
    }

    /** Builds the runtime sampler for [track]; cubic tracks get tangents from each key's [TangentMode]. */
    fun toSampler(track: SkTrack): AnimationSampler {
        val c = track.components
        val values = Tangents.values(track)
        val flat = if (track.interpolation == InterpolationType.CUBIC_SPLINE) {
            val tangents = Tangents.resolve(track)
            FloatArray(values.size * c * 3).also { out ->
                for (i in values.indices) {
                    tangents[i].inTangent.copyInto(out, i * 3 * c)
                    values[i].copyInto(out, i * 3 * c + c)
                    tangents[i].outTangent.copyInto(out, i * 3 * c + 2 * c)
                }
            }
        } else {
            FloatArray(values.size * c).also { out -> for (i in values.indices) values[i].copyInto(out, i * c) }
        }
        return AnimationSampler(track.times, flat, track.interpolation, c)
    }

    /** Runtime clip for [clip]; tracks without keys are skipped. [path] is the asset path the clip was loaded from. */
    fun toAnimation(clip: SkClip, path: String): Animation {
        val channels = clip.tracks.filter { it.keys.isNotEmpty() }.map { AnimationChannel(toSampler(it), it.bone, it.path) }
        return Animation(clip.name, channels, clip.duration, path)
    }

    /** Editable copy of a runtime clip. Cubic channels keep their exact tangents ([TangentMode.FREE]). */
    fun fromAnimation(animation: Animation, fps: Float = 30f, loop: Boolean = true): SkClip {
        val tracks = animation.channels.map { ch ->
            val s = ch.sampler
            val c = s.componentsPerValue
            val cubic = s.interpolation == InterpolationType.CUBIC_SPLINE
            val stride = if (cubic) 3 * c else c
            val keys = s.times.indices.map { i ->
                val base = i * stride
                if (cubic) SkKey(
                    s.times[i], s.values.slice(base + c until base + 2 * c), TangentMode.FREE,
                    s.values.slice(base until base + c), s.values.slice(base + 2 * c until base + 3 * c),
                ) else SkKey(s.times[i], s.values.slice(base until base + c))
            }
            // Importers can emit repeated times (ANI-01 L6); keep the first key of each time so the track stays valid.
            var last = Float.NEGATIVE_INFINITY
            SkTrack(ch.targetNodeName, ch.path, s.interpolation, keys.filter { k -> (k.time > last).also { ok -> if (ok) last = k.time } })
        }
        val lastKey = tracks.maxOfOrNull { t -> t.keys.lastOrNull()?.time ?: 0f } ?: 0f
        val duration = if (animation.duration > 0f) animation.duration else lastKey.coerceAtLeast(1f / fps)
        val humanoid = animation.channels.any { it.targetNodeName.equals("Hips", ignoreCase = true) }
        return SkClip(name = animation.name, fps = fps, duration = duration, loop = loop, humanoid = humanoid, tracks = tracks)
    }
}
