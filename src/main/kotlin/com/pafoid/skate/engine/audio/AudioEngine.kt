package com.pafoid.skate.engine.audio

import com.pafoid.skate.editor.systems.LoggerService
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import java.nio.IntBuffer

/**
 * Low-level OpenAL audio engine wrapper.
 *
 * Responsibilities:
 * - OpenAL device and context management
 * - Audio buffer loading (WAV, OGG)
 * - Source management (play, stop, position, volume)
 * - Listener state (position, orientation, velocity)
 *
 * This is a low-level wrapper. Use AudioSystem for ECS integration.
 */
class AudioEngine(
    private val logger: LoggerService
) {
    
    private var device: Long = 0L
    private var context: Long = 0L
    var isInitialized = false
        private set

    /**
     * Gets the OpenAL device handle (needed for capability setup).
     */
    fun getDevice(): Long = device
    
    /**
     * Initializes the OpenAL device and context.
     * Must be called on the main thread after OpenGL context is created.
     * @return true if successful
     */
    fun init(): Boolean {
        if (isInitialized) return true

        try {
            // Open default device
            device = ALC10.alcOpenDevice(null as CharSequence?)
            if (device == 0L) {
                logger.logEngine(
                    "AudioEngine: Failed to open OpenAL device",
                    com.pafoid.skate.editor.systems.LogLevel.ERROR
                )
                return false
            }

            // Create context
            context = ALC10.alcCreateContext(device, null as IntBuffer?)
            if (context == 0L) {
                logger.logEngine(
                    "AudioEngine: Failed to create OpenAL context",
                    com.pafoid.skate.editor.systems.LogLevel.ERROR
                )
                ALC10.alcCloseDevice(device)
                return false
            }

            // Make context current on this thread
            ALC10.alcMakeContextCurrent(context)

            // Create capabilities for this thread (required before AL10 functions work)
            val deviceCaps = ALC.createCapabilities(device)
            AL.createCapabilities(deviceCaps)

            isInitialized = true
            logger.logEngine("AudioEngine: Initialized successfully")
            return true

        } catch (e: Exception) {
            logger.logEngine("AudioEngine: Init failed - ${e.message}", com.pafoid.skate.editor.systems.LogLevel.ERROR)
            cleanup()
            return false
        }
    }
    
    /**
     * Destroys the OpenAL context and device.
     */
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
            logger.logEngine("AudioEngine: Destroyed")

        } catch (e: Exception) {
            logger.logEngine(
                "AudioEngine: Cleanup failed - ${e.message}",
                com.pafoid.skate.editor.systems.LogLevel.ERROR
            )
        }
    }
    
    private fun cleanup() {
        destroy()
    }

    /**
     * Gets the OpenAL context handle.
     */
    fun getContext(): Long = context

    /**
     * Makes this context current on the calling thread.
     */
    fun makeContextCurrent() {
        if (context != 0L) {
            ALC10.alcMakeContextCurrent(context)
        }
    }

    // ============ Listener Methods ============

    /**
     * Sets the listener position.
     */
    fun setListenerPosition(x: Float, y: Float, z: Float) {
        if (!isInitialized) return
        AL10.alListener3f(AL10.AL_POSITION, x, y, z)
    }

    /**
     * Sets the listener orientation.
     * @param forward Forward direction vector (normalized)
     * @param up Up direction vector (normalized)
     */
    fun setListenerOrientation(
        forwardX: Float, forwardY: Float, forwardZ: Float,
        upX: Float, upY: Float, upZ: Float
    ) {
        if (!isInitialized) return
        AL10.alListenerfv(
            AL10.AL_ORIENTATION, floatArrayOf(
                forwardX, forwardY, forwardZ, upX, upY, upZ
            )
        )
    }

    /**
     * Sets the listener velocity.
     */
    fun setListenerVelocity(x: Float, y: Float, z: Float) {
        if (!isInitialized) return
        AL10.alListener3f(AL10.AL_VELOCITY, x, y, z)
    }

    /**
     * Sets the master volume (gain).
     */
    fun setMasterVolume(gain: Float) {
        if (!isInitialized) return
        AL10.alListenerf(AL10.AL_GAIN, gain.coerceIn(0f, 1f))
    }
}
