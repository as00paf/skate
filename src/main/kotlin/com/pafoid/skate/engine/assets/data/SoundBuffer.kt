package com.pafoid.skate.engine.assets.data

import org.lwjgl.openal.AL10.alDeleteBuffers

class SoundBuffer(val filePath: String) {

    var bufferId: Int = -1
    var durationInSeconds: Float = 0f

    fun delete() {
        if (bufferId != -1) alDeleteBuffers(bufferId)
    }
}