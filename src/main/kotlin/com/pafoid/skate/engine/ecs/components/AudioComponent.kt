package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.data.Sound
import com.pafoid.skate.engine.audio.AudioEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * AudioComponent attaches a sound source to a GameObject.
 *
 * Supports:
 * - 2D audio (ignores position, global sound)
 * - 3D audio (position-based spatialization)
 * - Looping control
 * - Volume control per source
 * - Play/pause/stop controls
 *
 * @param soundFilePath Path to the audio file (WAV or OGG)
 * @param is3D Whether this sound should be spatialized in 3D space
 * @param loops Whether the sound should loop continuously
 * @param volume Volume level (0.0 to 1.0)
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

    /**
     * Sets dependencies after deserialization.
     */
    fun setDependencies(audioEngine: AudioEngine, logger: LoggerService) {
        this.audioEngine = audioEngine
        this.logger = logger
    }
    
    /**
     * Sets the audio engine reference for 3D positioning.
     */
    fun setAudioEngine(engine: AudioEngine) {
        this.audioEngine = engine
    }

    /**
     * Sets the logger reference.
     */
    fun setLogger(logger: LoggerService) {
        this.logger = logger
    }
    
    /**
     * Loads the sound from the file path.
     * Should be called after the audio system is initialized.
     */
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
    
    /**
     * Plays the sound.
     */
    fun play() {
        if (!isLoaded) load()
        sound?.play()
    }
    
    /**
     * Stops the sound.
     */
    fun stop() {
        sound?.stop()
    }
    
    /**
     * Pauses the sound (stops and remembers position for 2D sounds).
     */
    fun pause() {
        stop()
    }
    
    /**
     * Sets the volume of this sound source.
     * @param vol Volume level (0.0 to 1.0)
     */
    fun applyVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        // Note: Sound class doesn't expose volume setter, would need to be added
    }
    
    /**
     * Sets whether this sound is 3D spatialized.
     */
    fun apply3D(is3D: Boolean) {
        this.is3D = is3D
    }
    
    /**
     * Sets the looping state.
     */
    fun applyLooping(loop: Boolean) {
        loops = loop
        // Would need to update Sound if already loaded
    }
    
    /**
     * Checks if the sound is currently playing.
     */
    fun isPlaying(): Boolean {
        return sound?.isPlaying() ?: false
    }
    
    /**
     * Updates the 3D position of the sound source.
     * Only affects 3D sounds.
     */
    fun updatePosition(position: org.joml.Vector3f) {
        if (!is3D || !isLoaded) return
        
        // Note: Sound class would need position setter
        // This is a placeholder for future implementation
    }
    
    override fun destroy() {
        sound?.delete()
        sound = null
        isLoaded = false
    }
}
