package com.pafoid.skate.engine.audio

import kotlinx.serialization.Serializable

@Serializable
enum class BusId { MASTER, MUSIC, SFX, UI }

/** Engine-side audio settings, decoupled from the editor's `AudioSettings`. */
data class AudioConfig(
    val masterVolume: Float = 1.0f,
    val musicVolume: Float = 0.8f,
    val sfxVolume: Float = 1.0f,
    val uiVolume: Float = 1.0f,
    val masterMuted: Boolean = false,
    val musicMuted: Boolean = false,
    val sfxMuted: Boolean = false,
    val uiMuted: Boolean = false
)

class AudioBus(val id: BusId, val parent: AudioBus? = null) {
    var volume: Float = 1.0f
        set(value) {
            field = sanitize(value)
        }
    var muted: Boolean = false

    /** volume x mute, multiplied through the parent chain. */
    val effectiveGain: Float
        get() = (if (muted) 0f else volume) * (parent?.effectiveGain ?: 1f)

    private fun sanitize(value: Float) = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
}

class Mixer {
    private val master = AudioBus(BusId.MASTER)
    private val buses: Map<BusId, AudioBus> = mapOf(
        BusId.MASTER to master,
        BusId.MUSIC to AudioBus(BusId.MUSIC, master),
        BusId.SFX to AudioBus(BusId.SFX, master),
        BusId.UI to AudioBus(BusId.UI, master)
    )

    fun bus(id: BusId): AudioBus = buses.getValue(id)

    fun applyConfig(config: AudioConfig) {
        configure(BusId.MASTER, config.masterVolume, config.masterMuted)
        configure(BusId.MUSIC, config.musicVolume, config.musicMuted)
        configure(BusId.SFX, config.sfxVolume, config.sfxMuted)
        configure(BusId.UI, config.uiVolume, config.uiMuted)
    }

    private fun configure(id: BusId, volume: Float, muted: Boolean) {
        bus(id).apply {
            this.volume = volume
            this.muted = muted
        }
    }

    /** Final source gain for a sound with the given own volume played on [id]. */
    fun sourceGain(id: BusId, sourceVolume: Float): Float {
        val own = if (sourceVolume.isNaN()) 0f else sourceVolume.coerceIn(0f, 1f)
        return own * bus(id).effectiveGain
    }
}
