package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.Transform
import org.joml.Vector3f
import org.lwjgl.openal.ALC10

/**
 * ECS system for audio playback and 3D spatialization.
 *
 * Responsibilities:
 * - Initialize AudioEngine on first update (when context is current)
 * - Update listener position/orientation from camera
 * - Update 3D audio source positions from transforms
 * - Manage audio component lifecycle
 *
 * Runs at ExecutionPriority.LATE to ensure transforms are updated first.
 */
class AudioSystem(
    private val audioEngine: AudioEngine,
    private val logger: LoggerService
) : System(priority = ExecutionPriority.LATE) {

    private var isInitialized = false
    
    override fun start() {
        // Don't initialize AudioEngine here - wait for first update
        // when we're on the main thread with context current
    }
    
    override fun update(dt: Float) {
        // Lazy init on first update (guaranteed to be on main thread)
        if (!isInitialized) {
            if (audioEngine.init()) {
                isInitialized = true
                logger.logEngine("AudioSystem: Audio initialized")
            } else {
                // Silently fail - audio is optional
                logger.logEngine(
                    "AudioSystem: Failed to initialize - audio disabled",
                    com.pafoid.skate.editor.systems.LogLevel.WARN
                )
                return
            }
        }

        // Update listener from camera
        updateListener()
        
        // Update 3D audio source positions
        updateAudioSources()
    }
    
    override fun editorUpdate(dt: Float) {
        // Same as update for editor
        update(dt)
    }
    
    /**
     * Updates the OpenAL listener from the scene camera.
     */
    private fun updateListener() {
        if (!audioEngine.isInitialized) return

        // Ensure context is current on THIS thread
        val currentContext = ALC10.alcGetCurrentContext()
        if (currentContext != audioEngine.getContext()) {
            audioEngine.makeContextCurrent()
        }

        // Ensure capabilities are set for this thread
        // (ALC.createCapabilities is thread-local)
        if (!org.lwjgl.openal.ALC.getCapabilities().OpenALC10) {
            // Capabilities not set for this thread, initialize them
            org.lwjgl.openal.ALC.createCapabilities(audioEngine.getDevice())
        }

        val camera = scene.camera
        val pos = camera.position

        // Calculate forward vector from pitch and yaw
        val forward = Vector3f()
        forward.set(
            kotlin.math.cos(Math.toRadians(camera.yaw.toDouble())).toFloat() *
                    kotlin.math.cos(Math.toRadians(camera.pitch.toDouble())).toFloat(),
            kotlin.math.sin(Math.toRadians(camera.pitch.toDouble())).toFloat(),
            kotlin.math.sin(Math.toRadians(camera.yaw.toDouble())).toFloat() *
                    kotlin.math.cos(Math.toRadians(camera.pitch.toDouble())).toFloat()
        ).normalize()

        // Update listener
        audioEngine.setListenerPosition(pos.x, pos.y, pos.z)
        audioEngine.setListenerOrientation(
            forward.x, forward.y, forward.z,
            0f, 1f, 0f  // World up
        )
        audioEngine.setListenerVelocity(0f, 0f, 0f)  // Static listener for now
        audioEngine.setMasterVolume(1.0f)
    }
    
    /**
     * Updates 3D audio source positions from their GameObject transforms.
     */
    private fun updateAudioSources() {
        scene.gameObjectManager.gameObjects.forEach { gameObject ->
            val audioComponent = gameObject.getComponent<AudioComponent>()
            val transform = gameObject.getComponent<Transform>()
            
            if (audioComponent != null && transform != null && audioComponent.is3D) {
                // Update source position based on transform
                // (Sound class would need position setter for this)
                val pos = transform.translation
                // audioComponent.setPosition(pos.x, pos.y, pos.z)
            }
        }
    }
    
    override fun destroy() {
        // Don't destroy AudioEngine here - it may be shared
        isInitialized = false
    }
}
