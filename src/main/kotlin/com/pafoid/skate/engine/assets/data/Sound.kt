package com.pafoid.skate.engine.assets.data

import org.lwjgl.openal.AL10.AL_BUFFER
import org.lwjgl.openal.AL10.AL_FORMAT_MONO16
import org.lwjgl.openal.AL10.AL_FORMAT_STEREO16
import org.lwjgl.openal.AL10.AL_GAIN
import org.lwjgl.openal.AL10.AL_LOOPING
import org.lwjgl.openal.AL10.AL_POSITION
import org.lwjgl.openal.AL10.AL_SOURCE_STATE
import org.lwjgl.openal.AL10.AL_STOPPED
import org.lwjgl.openal.AL10.alBufferData
import org.lwjgl.openal.AL10.alDeleteBuffers
import org.lwjgl.openal.AL10.alDeleteSources
import org.lwjgl.openal.AL10.alGenBuffers
import org.lwjgl.openal.AL10.alGenSources
import org.lwjgl.openal.AL10.alGetSourcei
import org.lwjgl.openal.AL10.alSourcePlay
import org.lwjgl.openal.AL10.alSourceStop
import org.lwjgl.openal.AL10.alSourcef
import org.lwjgl.openal.AL10.alSourcei
import org.lwjgl.stb.STBVorbis.stb_vorbis_decode_filename
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.libc.LibCStdlib.free

/**
 * Sound resource for OpenAL audio playback.
 *
 * Loads and manages a single audio buffer and source.
 * Supports WAV and OGG formats via STB Vorbis decoder.
 */
class Sound(
    val filePath: String,
    val loops: Boolean = false
) {

    private var bufferId: Int = -1
    private var sourceId: Int = -1
    private var isPlaying = false

    init {
        load()
    }

    private fun load() {
        MemoryStack.stackPush().use { stack ->
            val channelsBuffer = stack.mallocInt(1)
            val sampleRateBuffer = stack.mallocInt(1)

            val rawAudioBuffer = stb_vorbis_decode_filename(filePath, channelsBuffer, sampleRateBuffer)
                ?: run {
                    println("Error: could not load sound $filePath")
                    return
                }

            val channels = channelsBuffer.get()
            val sampleRate = sampleRateBuffer.get()
            val format = if (channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16

            bufferId = alGenBuffers()
            alBufferData(bufferId, format, rawAudioBuffer, sampleRate)
            free(rawAudioBuffer)

            sourceId = alGenSources()
            alSourcei(sourceId, AL_BUFFER, bufferId)
            alSourcei(sourceId, AL_LOOPING, if (loops) 1 else 0)
            alSourcef(sourceId, AL_GAIN, 0.3f)
        }
    }

    fun delete() {
        if (sourceId != -1) alDeleteSources(sourceId)
        if (bufferId != -1) alDeleteBuffers(bufferId)
    }

    fun play() {
        if (alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_STOPPED) {
            isPlaying = false
            alSourcei(sourceId, AL_POSITION, 0)
        }

        if (!isPlaying) {
            alSourcePlay(sourceId)
            isPlaying = true
        }
    }

    fun stop() {
        if (isPlaying) {
            alSourceStop(sourceId)
            isPlaying = false
        }
    }

    fun isPlaying(): Boolean {
        if (alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_STOPPED) {
            isPlaying = false
        }
        return isPlaying
    }
}
