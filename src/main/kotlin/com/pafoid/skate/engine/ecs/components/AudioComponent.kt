package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * AudioComponent is a pure data container for a sound source attached to a GameObject.
 * 
 * Supports 2D and 3D audio with spatialization, looping, and volume control.
 * Playback logic and OpenAL interactions are handled by AudioSystem.
 */
@Serializable
class AudioComponent(
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

    fun stop() {
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
