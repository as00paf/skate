package com.pafoid.skate.engine.audio

import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.AL10
import org.lwjgl.system.MemoryUtil
import java.nio.IntBuffer

/**
 * AudioEngine manages the OpenAL context and provides global audio controls.
 * 
 * Responsibilities:
 * - Initialize and destroy OpenAL context
 * - Manage audio listener (position, orientation, velocity)
 * - Global volume control
 * - Audio capability checking
 * 
 * Usage:
 * ```kotlin
 * val audioEngine = AudioEngine()
 * audioEngine.init()
 * // ... use audio system ...
 * audioEngine.destroy()
 * ```
 */
class AudioEngine {
    
    private var device: Long = 0L
    private var context: Long = 0L
    private var isInitialized = false
    
    // Global volume (0.0 to 1.0)
    var masterVolume: Float = 1.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            // Update listener gain if needed
        }
    
    // Listener position (3D)
    var listenerPosition: org.joml.Vector3f = org.joml.Vector3f(0f, 0f, 0f)
        set(value) {
            field = value
            updateListener()
        }
    
    // Listener velocity (3D)
    var listenerVelocity: org.joml.Vector3f = org.joml.Vector3f(0f, 0f, 0f)
        set(value) {
            field = value
            updateListener()
        }
    
    // Listener orientation (forward and up vectors)
    var listenerOrientation: Pair<org.joml.Vector3f, org.joml.Vector3f> = 
        Pair(org.joml.Vector3f(0f, 0f, -1f), org.joml.Vector3f(0f, 1f, 0f))
        set(value) {
            field = value
            updateListener()
        }
    
    /**
     * Initializes the OpenAL context and device.
     * @return true if initialization was successful
     */
    fun init(): Boolean {
        if (isInitialized) {
            return true
        }
        
        try {
            // Open default device
            device = ALC10.alcOpenDevice((null as CharSequence?)?.let { MemoryUtil.memUTF8(it) })
            if (device == 0L) {
                println("AudioEngine: Failed to open default OpenAL device")
                return false
            }
            
            // Create context
            context = ALC10.alcCreateContext(device, null as IntBuffer?)
            if (context == 0L) {
                println("AudioEngine: Failed to create OpenAL context")
                ALC10.alcCloseDevice(device)
                return false
            }
            
            // Make context current
            ALC10.alcMakeContextCurrent(context)
            
            // Set initial listener state
            updateListener()
            
            isInitialized = true
            println("AudioEngine: Initialized successfully")
            return true
            
        } catch (e: Exception) {
            println("AudioEngine: Initialization failed - ${e.message}")
            cleanup()
            return false
        }
    }
    
    /**
     * Updates the OpenAL listener parameters.
     */
    private fun updateListener() {
        if (!isInitialized) return
        
        // Import AL10 for listener functions
        org.lwjgl.openal.AL10.alListener3f(
            org.lwjgl.openal.AL10.AL_POSITION,
            listenerPosition.x,
            listenerPosition.y,
            listenerPosition.z
        )
        
        org.lwjgl.openal.AL10.alListener3f(
            org.lwjgl.openal.AL10.AL_VELOCITY,
            listenerVelocity.x,
            listenerVelocity.y,
            listenerVelocity.z
        )
        
        // Orientation: forward X, Y, Z, then up X, Y, Z
        val (forward, up) = listenerOrientation
        org.lwjgl.openal.AL10.alListenerfv(
            org.lwjgl.openal.AL10.AL_ORIENTATION,
            floatArrayOf(
                forward.x, forward.y, forward.z,
                up.x, up.y, up.z
            )
        )
        
        // Master volume via gain
        org.lwjgl.openal.AL10.alListenerf(
            org.lwjgl.openal.AL10.AL_GAIN,
            masterVolume
        )
    }
    
    /**
     * Checks if OpenAL is capable of playing audio.
     */
    fun isCapable(): Boolean {
        return isInitialized
    }
    
    /**
     * Destroys the OpenAL context and closes the device.
     */
    fun destroy() {
        if (!isInitialized) return
        
        try {
            // Destroy context
            if (context != 0L) {
                ALC10.alcDestroyContext(context)
                context = 0L
            }
            
            // Close device
            if (device != 0L) {
                ALC10.alcCloseDevice(device)
                device = 0L
            }
            
            isInitialized = false
            println("AudioEngine: Destroyed successfully")
            
        } catch (e: Exception) {
            println("AudioEngine: Cleanup failed - ${e.message}")
        }
    }
    
    /**
     * Cleanup alias for destroy.
     */
    private fun cleanup() {
        destroy()
    }
}
