package com.pafoid.skate.engine.assets.data

import org.lwjgl.openal.AL10.AL_BUFFER
import org.lwjgl.openal.AL10.AL_BUFFERS_PROCESSED
import org.lwjgl.openal.AL10.AL_BUFFERS_QUEUED
import org.lwjgl.openal.AL10.AL_STOPPED
import org.lwjgl.openal.AL10.AL_FORMAT_MONO16
import org.lwjgl.openal.AL10.AL_FORMAT_STEREO16
import org.lwjgl.openal.AL10.alBufferData
import org.lwjgl.openal.AL10.alDeleteBuffers
import org.lwjgl.openal.AL10.alGenBuffers
import org.lwjgl.openal.AL10.alSourceQueueBuffers
import org.lwjgl.openal.AL10.alSourceUnqueueBuffers
import org.lwjgl.openal.AL10.AL_FALSE
import org.lwjgl.openal.AL10.AL_GAIN
import org.lwjgl.openal.AL10.AL_LOOPING
import org.lwjgl.openal.AL10.AL_PLAYING
import org.lwjgl.openal.AL10.AL_PITCH
import org.lwjgl.openal.AL10.AL_POSITION
import org.lwjgl.openal.AL10.AL_VELOCITY
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

import com.pafoid.skate.engine.audio.StreamSink

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

    fun setVelocity(x: Float, y: Float, z: Float) {
        alSource3f(sourceId, AL_VELOCITY, x, y, z)
    }

    fun setPitch(pitch: Float) {
        alSourcef(sourceId, AL_PITCH, pitch.coerceIn(0.01f, 8f))
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
        alSourceStop(sourceId)
    }

    fun pause() {
        if (isPlaying()) {
            alSourcePause(sourceId)
        }
    }

    fun isPlaying(): Boolean {
        return alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PLAYING
    }

    /** Detaches any static buffer and returns a sink that streams PCM through this source. */
    fun asStreamSink(): StreamSink {
        stop()
        alSourcei(sourceId, AL_BUFFER, 0)
        return object : StreamSink {
            override fun createBuffers(count: Int) = IntArray(count) { alGenBuffers() }
            override fun deleteBuffers(ids: IntArray) = ids.forEach { alDeleteBuffers(it) }
            override fun upload(bufferId: Int, pcm: ShortArray, sampleCount: Int, channels: Int, sampleRate: Int) {
                alBufferData(bufferId, if (channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16, pcm.copyOf(sampleCount), sampleRate)
            }
            override fun queue(bufferId: Int) = alSourceQueueBuffers(sourceId, bufferId)
            override fun unqueueProcessed(): IntArray {
                val n = alGetSourcei(sourceId, AL_BUFFERS_PROCESSED)
                return IntArray(n) { alSourceUnqueueBuffers(sourceId) }
            }
            override fun queuedCount() = alGetSourcei(sourceId, AL_BUFFERS_QUEUED)
            override fun isStopped() = alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_STOPPED
            override fun play() = alSourcePlay(sourceId)
        }
    }

    fun delete() {
        stop()
        if (sourceId != -1) {
            alSourcei(sourceId, AL_BUFFER, 0)
            alDeleteSources(sourceId)
        }
    }
}
