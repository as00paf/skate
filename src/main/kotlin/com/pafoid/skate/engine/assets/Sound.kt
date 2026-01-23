package com.pafoid.skate.engine.assets

import org.lwjgl.openal.AL10.*
import org.lwjgl.stb.STBVorbis.stb_vorbis_decode_filename
import org.lwjgl.system.MemoryStack.*
import org.lwjgl.system.libc.LibCStdlib.free


class Sound(val filePath:String, val loops: Boolean) {

    private var bufferId = -1
    private var sourceId = -1
    private var isPlaying = false

    init {
        loadSound()
    }

    private fun loadSound() {
        // Allocate space to store the return info from stb
        stackPush()
        val channelsBuffer = stackMallocInt(1)
        stackPush()
        val sampleRateBuffer = stackMallocInt(1)

        val rawAudioBuffer = stb_vorbis_decode_filename(filePath, channelsBuffer, sampleRateBuffer)
        if(rawAudioBuffer == null) {
            println("Error: could not load sound $filePath")
            stackPop()
            stackPop()
            return
        }

        // Retrieve info that was stored in buffers
        val channels = channelsBuffer.get()
        val sampleRate = sampleRateBuffer.get()

        //Free memory
        stackPop()
        stackPop()

        // Find correct openAL format
        val format = if(channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16

        bufferId = alGenBuffers()
        alBufferData(bufferId, format, rawAudioBuffer, sampleRate)

        // Generate the source
        sourceId = alGenSources()

        val shouldLoop = if(loops) 1 else 0
        alSourcei(sourceId, AL_BUFFER, bufferId)
        alSourcei(sourceId, AL_LOOPING, shouldLoop)
        alSourcei(sourceId, AL_POSITION, 0)
        alSourcef(sourceId, AL_GAIN, 0.3f)

        // Free buffer
        free(rawAudioBuffer)
    }

    fun delete() {
        alDeleteSources(sourceId)
        alDeleteBuffers(bufferId)
    }

    fun play() {
        val state = alGetSourcei(sourceId, AL_SOURCE_STATE)
        if(state == AL_STOPPED) {
            isPlaying = false
            alSourcei(sourceId, AL_POSITION, 0)
        }

        if(!isPlaying) {
            alSourcePlay(sourceId)
            isPlaying = true
        }
    }

    fun stop() {
        if(isPlaying) {
            alSourceStop(sourceId)
            isPlaying = false
        }
    }

    fun isPlaying(): Boolean {
        val state = alGetSourcei(sourceId, AL_SOURCE_STATE)
        if(state == AL_STOPPED) {
            isPlaying = false
        }
        return isPlaying
    }
}