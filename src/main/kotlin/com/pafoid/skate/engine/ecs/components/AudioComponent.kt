package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.audio.AttenuationModel
import com.pafoid.skate.engine.audio.BusId
import com.pafoid.skate.engine.audio.StreamMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class AudioComponent(
    var soundFilePath: String = "",
    var is3D: Boolean = true,
    var loops: Boolean = false,
    var volume: Float = 1.0f,
    var bus: BusId = BusId.SFX,
    var attenuationModel: AttenuationModel = AttenuationModel.INVERSE_CLAMPED,
    var minDistance: Float = 1f,
    var maxDistance: Float = 50f,
    var rolloff: Float = 1f,
    var dopplerFactor: Float = 1f,
    /** Higher wins when voices run out. */
    var priority: Int = 128,
    /** When not empty, a random clip (never the same twice in a row) is played instead of [soundFilePath]. */
    var clips: List<String> = emptyList(),
    var streamMode: StreamMode = StreamMode.AUTO,
    var playOnAwake: Boolean = false
) : Component() {

    @Transient
    var isPlaying: Boolean = false
    
    @Transient
    var isPaused: Boolean = false

    @Transient
    var awoken: Boolean = false

    @Transient
    var playRequested: Boolean = false
    
    @Transient
    var stopRequested: Boolean = false
    
    @Transient
    var pauseRequested: Boolean = false

    fun play() {
        playRequested = true
        stopRequested = false
        pauseRequested = false
    }

    fun stopPlayback() {
        stopRequested = true
        playRequested = false
        pauseRequested = false
    }

    fun pause() {
        pauseRequested = true
        playRequested = false
        stopRequested = false
    }

    fun applyVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
    }

    fun apply3D(is3D: Boolean) {
        this.is3D = is3D
    }

    fun applyLooping(loop: Boolean) {
        this.loops = loop
    }

    override fun destroy() {
        // Handled by AudioSystem which will detect removal or cleanup
    }
}
