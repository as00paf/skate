package com.pafoid.skate.engine.assets.data

import org.lwjgl.openal.AL10.AL_BUFFER
import org.lwjgl.openal.AL10.AL_FALSE
import org.lwjgl.openal.AL10.AL_GAIN
import org.lwjgl.openal.AL10.AL_LOOPING
import org.lwjgl.openal.AL10.AL_PLAYING
import org.lwjgl.openal.AL10.AL_POSITION
import org.lwjgl.openal.AL10.AL_SOURCE_RELATIVE
import org.lwjgl.openal.AL10.AL_SOURCE_STATE
import org.lwjgl.openal.AL10.AL_TRUE
import org.lwjgl.openal.AL10.alDeleteSources
import org.lwjgl.openal.AL10.alGenSources
import org.lwjgl.openal.AL10.alGetSourcei
import org.lwjgl.openal.AL10.alSource3f
import org.lwjgl.openal.AL10.alSourcePause
import org.lwjgl.openal.AL10.alSourcePlay
import org.lwjgl.openal.AL10.alSourceStop
import org.lwjgl.openal.AL10.alSourcef
import org.lwjgl.openal.AL10.alSourcei

/**
 * Sound source for OpenAL audio playback.
 * 
 * Manages an OpenAL source and its properties (position, volume, looping).
 */
class SoundSource(isLooping: Boolean, isRelative: Boolean) {

    var sourceId: Int = -1
        private set

    init {
        sourceId = alGenSources()
        setLooping(isLooping)
        setRelative(isRelative)
        // Set default volume, previously hardcoded to 0.3f
        setVolume(1.0f)
    }

    fun setBuffer(bufferId: Int) {
        stop()
        alSourcei(sourceId, AL_BUFFER, bufferId)
    }

    fun setPosition(x: Float, y: Float, z: Float) {
        alSource3f(sourceId, AL_POSITION, x, y, z)
    }

    fun setVolume(volume: Float) {
        alSourcef(sourceId, AL_GAIN, volume)
    }

    fun setLooping(loop: Boolean) {
        alSourcei(sourceId, AL_LOOPING, if (loop) AL_TRUE else AL_FALSE)
    }

    fun setRelative(relative: Boolean) {
        alSourcei(sourceId, AL_SOURCE_RELATIVE, if (relative) AL_TRUE else AL_FALSE)
    }

    fun play() {
        if (alGetSourcei(sourceId, AL_SOURCE_STATE) != AL_PLAYING) {
            alSourcePlay(sourceId)
        }
    }

    fun stop() {
        if (isPlaying()) {
            alSourceStop(sourceId)
            alSourcei(sourceId, AL_POSITION, 0)
        }
    }

    fun pause() {
        if (isPlaying()) {
            alSourcePause(sourceId)
        }
    }

    fun isPlaying(): Boolean {
        return alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PLAYING
    }

    fun delete() {
        stop()
        if (sourceId != -1) {
            alSourcei(sourceId, AL_BUFFER, 0)
            alDeleteSources(sourceId)
        }
    }
}
