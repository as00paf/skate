package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class AudioComponent(
    var soundFilePath: String = "",
    var is3D: Boolean = true,
    var loops: Boolean = false,
    var volume: Float = 1.0f
) : Component() {

    @Transient
    var isPlaying: Boolean = false
    
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
