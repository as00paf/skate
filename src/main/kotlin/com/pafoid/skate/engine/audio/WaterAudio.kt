package com.pafoid.skate.engine.audio

import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Engine-wide water sounds (AUD-01, REN-29 task 16): a splash when a body enters a water volume (PHY-16's
 * `WaterEntered`) and a looping ambience heard near every `WaterComponent`. These are the defaults; a
 * `WaterComponent` overrides any of them with its own audio fields (AUD-15, [withOverrides]).
 */
data class WaterAudioConfig(
    val enabled: Boolean = true,
    /** One is picked at random per splash, never the same twice in a row. */
    val splashClips: List<String> = WaterAudio.DEFAULT_SPLASHES,
    val splashVolume: Float = 1f,
    val splashMinDistance: Float = 2f,
    val splashMaxDistance: Float = 40f,
    /** A body that re-enters the water within this many seconds does not splash again (bobbing at the edge). */
    val splashCooldown: Float = 0.5f,
    /** At most this many splashes start per frame; a crowd falling in at once does not take every voice. */
    val maxSplashesPerFrame: Int = 4,
    val splashPriority: Int = 96,
    val ambienceClip: String = WaterAudio.DEFAULT_AMBIENCE,
    val ambienceVolume: Float = 0.5f,
    /** Full volume within this distance of the water's edge. */
    val ambienceMinDistance: Float = 3f,
    /** Silent and released beyond this distance from the water's edge. */
    val ambienceMaxDistance: Float = 30f,
    val ambiencePriority: Int = 64,
) {
    /** This config with each non-null value replacing its default: one water's own sounds (AUD-15). */
    fun withOverrides(
        ambienceClip: String? = null,
        ambienceVolume: Float? = null,
        ambienceMinDistance: Float? = null,
        ambienceMaxDistance: Float? = null,
        splashClips: List<String>? = null,
        splashVolume: Float? = null,
        splashMinDistance: Float? = null,
        splashMaxDistance: Float? = null,
    ): WaterAudioConfig = copy(
        ambienceClip = ambienceClip ?: this.ambienceClip,
        ambienceVolume = ambienceVolume ?: this.ambienceVolume,
        ambienceMinDistance = ambienceMinDistance ?: this.ambienceMinDistance,
        ambienceMaxDistance = ambienceMaxDistance ?: this.ambienceMaxDistance,
        splashClips = splashClips?.takeIf { it.isNotEmpty() } ?: this.splashClips,
        splashVolume = splashVolume ?: this.splashVolume,
        splashMinDistance = splashMinDistance ?: this.splashMinDistance,
        splashMaxDistance = splashMaxDistance ?: this.splashMaxDistance,
    )
}

object WaterAudio {
    const val DEFAULT_AMBIENCE = "engine:Sounds/Water/water_ambience_loop.wav"
    val DEFAULT_SPLASHES = listOf(
        "engine:Sounds/Water/splash_01.wav",
        "engine:Sounds/Water/splash_02.wav",
        "engine:Sounds/Water/splash_03.wav",
    )

    /** Impact speed (m/s) at and above which a splash plays at full volume. */
    const val FULL_SPLASH_SPEED = 8f

    /** Volume of the quietest splash (a body drifting in). */
    const val MIN_SPLASH_GAIN = 0.25f

    /**
     * Splash loudness for a body entering at [velocity]: the downward speed matters most, sideways motion counts for
     * half (skimming in). [MIN_SPLASH_GAIN] at rest, 1 at [FULL_SPLASH_SPEED].
     */
    fun splashGain(velocity: Vector3f): Float {
        val down = if (velocity.y < 0f) -velocity.y else 0f
        val side = kotlin.math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
        val speed = down + side * 0.5f
        val t = (speed / FULL_SPLASH_SPEED).coerceIn(0f, 1f)
        return MIN_SPLASH_GAIN + (1f - MIN_SPLASH_GAIN) * t
    }

    /**
     * The point of a water surface rectangle ([sizeX] × [sizeZ] m centred on [center], turned by [yaw] radians as in
     * `WaterQueries`: `yaw = atan2(m20, m22)` of the world matrix) closest to [listener], at the surface height. The
     * ambience plays from there, so a large pool sounds as near as its nearest edge, and from below the listener
     * when they stand over it.
     */
    fun nearestSurfacePoint(
        center: Vector3f, yaw: Float, sizeX: Float, sizeZ: Float, listener: Vector3f, out: Vector3f = Vector3f(),
    ): Vector3f {
        val c = cos(yaw)
        val s = sin(yaw)
        val dx = listener.x - center.x
        val dz = listener.z - center.z
        // Into the water's frame (inverse yaw), clamp to the rectangle, then back to world
        val lx = (dx * c - dz * s).coerceIn(-abs(sizeX) / 2f, abs(sizeX) / 2f)
        val lz = (dx * s + dz * c).coerceIn(-abs(sizeZ) / 2f, abs(sizeZ) / 2f)
        return out.set(center.x + lx * c + lz * s, center.y, center.z - lx * s + lz * c)
    }
}

/** Per-body splash cooldown, on the audio system's own clock. Pure, so it is testable without a scene. */
class SplashCooldown {
    private val lastSplash = HashMap<Int, Float>()

    /** True when [body] may splash at [now] (none in the last [cooldown] seconds), and records it. */
    fun tryStart(body: Int, now: Float, cooldown: Float): Boolean {
        val last = lastSplash[body]
        if (last != null && now - last < cooldown) return false
        lastSplash[body] = now
        return true
    }

    fun clear() = lastSplash.clear()
}
