package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.data.Sound
import com.pafoid.skate.engine.audio.AudioEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f

/**
 * AudioComponent attaches a sound source to a GameObject.
 *
 * Supports 2D and 3D audio with spatialization, looping, and volume control.
 */
@Serializable
class AudioComponent(
    var soundFilePath: String = "",
    var is3D: Boolean = true,
    var loops: Boolean = false,
    var volume: Float = 1.0f
) : Component() {

    @Transient
    private var sound: Sound? = null

    @Transient
    private var isLoaded = false

    @Transient
    private var audioEngine: AudioEngine? = null

    @Transient
    private var logger: LoggerService? = null

    fun setDependencies(audioEngine: AudioEngine, logger: LoggerService) {
        this.audioEngine = audioEngine
        this.logger = logger
    }

    fun load() {
        if (isLoaded || soundFilePath.isBlank()) return

        try {
            sound = Sound(soundFilePath, loops)
            applyVolume(volume)
            isLoaded = true
        } catch (e: Exception) {
            logger?.logEngine("AudioComponent: Failed to load sound '$soundFilePath' - ${e.message}", LogLevel.ERROR)
        }
    }

    fun play() {
        if (!isLoaded) load()
        sound?.play()
    }

    fun stop() {
        sound?.stop()
    }

    fun pause() {
        stop()
    }

    fun applyVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
    }

    fun apply3D(is3D: Boolean) {
        this.is3D = is3D
    }

    fun applyLooping(loop: Boolean) {
        loops = loop
    }

    fun isPlaying(): Boolean {
        return sound?.isPlaying() ?: false
    }

    fun updatePosition(position: Vector3f) {
        if (!is3D || !isLoaded) return
        // TODO: Implement 3D position update in Sound class
    }

    override fun destroy() {
        sound?.delete()
        sound = null
        isLoaded = false
    }
}
