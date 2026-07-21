package com.pafoid.skate.engine.assets.data

import org.lwjgl.openal.AL10.alDeleteBuffers

/**
 * Sound buffer resource for OpenAL audio playback.
 * 
 * Loads and manages a single audio buffer.
 * Supports WAV (via Java Sound) and OGG (via STB Vorbis) formats.
 */
class SoundBuffer(val filePath: String) {

    var bufferId: Int = -1
    var durationInSeconds: Float = 0f

    fun delete() {
        if (bufferId != -1) alDeleteBuffers(bufferId)
    }
}