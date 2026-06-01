package com.pafoid.skate.engine.audio

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.data.LogLevel
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import java.nio.IntBuffer

/**
 * Low-level OpenAL audio engine wrapper.
 *
 * Manages OpenAL device, context, and listener state.
 * Thread-safe context management for multi-threaded audio operations.
 */
class AudioEngine(
    private val logger: LoggerService
) {

    private var device: Long = 0L
    private var context: Long = 0L
    var isInitialized = false
        private set

    fun init(): Boolean {
        if (isInitialized) return true

        try {
            device = ALC10.alcOpenDevice(null as CharSequence?)
            if (device == 0L) {
                logger.log("AudioEngine: Failed to open OpenAL device", LogLevel.ERROR)
                return false
            }

            context = ALC10.alcCreateContext(device, null as IntBuffer?)
            if (context == 0L) {
                logger.log("AudioEngine: Failed to create OpenAL context", LogLevel.ERROR)
                ALC10.alcCloseDevice(device)
                return false
            }

            ALC10.alcMakeContextCurrent(context)
            val deviceCaps = ALC.createCapabilities(device)
            AL.createCapabilities(deviceCaps)

            isInitialized = true
            logger.log("AudioEngine: Initialized successfully")
            return true

        } catch (e: Exception) {
            logger.log("AudioEngine: Init failed - ${e.message}", LogLevel.ERROR)
            destroy()
            return false
        }
    }

    fun destroy() {
        if (!isInitialized) return

        try {
            val currentContext = ALC10.alcGetCurrentContext()
            if (currentContext == context) {
                ALC10.alcMakeContextCurrent(0L)
            }

            if (context != 0L) {
                ALC10.alcDestroyContext(context)
                context = 0L
            }

            if (device != 0L) {
                ALC10.alcCloseDevice(device)
                device = 0L
            }

            isInitialized = false
            logger.log("AudioEngine: Destroyed")

        } catch (e: Exception) {
            logger.log("AudioEngine: Cleanup failed - ${e.message}", LogLevel.ERROR)
        }
    }

    fun getContext(): Long = context

    fun getDevice(): Long = device

    fun makeContextCurrent() {
        if (context != 0L) {
            ALC10.alcMakeContextCurrent(context)
        }
    }

    fun setListenerPosition(x: Float, y: Float, z: Float) {
        if (!isInitialized) return
        AL10.alListener3f(AL10.AL_POSITION, x, y, z)
    }

    fun setListenerOrientation(forward: FloatArray, up: FloatArray) {
        if (!isInitialized) return
        AL10.alListenerfv(AL10.AL_ORIENTATION, forward + up)
    }

    fun setListenerVelocity(x: Float, y: Float, z: Float) {
        if (!isInitialized) return
        AL10.alListener3f(AL10.AL_VELOCITY, x, y, z)
    }

    fun setMasterVolume(gain: Float) {
        if (!isInitialized) return
        AL10.alListenerf(AL10.AL_GAIN, gain.coerceIn(0f, 1f))
    }
}
