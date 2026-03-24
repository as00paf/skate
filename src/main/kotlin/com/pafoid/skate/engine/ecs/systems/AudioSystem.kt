package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.Transform
import org.joml.Vector3f

/**
 * AudioSystem updates 3D audio positions and manages audio playback.
 * 
 * Responsibilities:
 * - Update 3D sound positions based on GameObject transforms
 * - Update listener position from camera
 * - Manage audio component lifecycle
 * 
 * Runs at ExecutionPriority.LATE to ensure transforms are updated first.
 */
class AudioSystem(
    private val audioEngine: AudioEngine
) : System(priority = ExecutionPriority.LATE) {
    
    override fun start() {
        // Initialize audio engine if not already done
        if (!audioEngine.isCapable()) {
            audioEngine.init()
        }
    }
    
    override fun update(dt: Float) {
        if (!audioEngine.isCapable()) return
        
        // Update listener position from camera
        updateListener()
        
        // Update 3D audio source positions
        updateAudioSources()
    }
    
    override fun editorUpdate(dt: Float) {
        // Same update logic for editor
        update(dt)
    }
    
    /**
     * Updates the audio listener position and orientation from the scene camera.
     */
    private fun updateListener() {
        scene.camera.let { camera ->
            // Get camera position and calculate forward direction
            val position = camera.position
            
            // Calculate forward vector from pitch and yaw
            val forward = Vector3f()
            forward.set(
                kotlin.math.cos(Math.toRadians(camera.yaw.toDouble())).toFloat() *
                    kotlin.math.cos(Math.toRadians(camera.pitch.toDouble())).toFloat(),
                kotlin.math.sin(Math.toRadians(camera.pitch.toDouble())).toFloat(),
                kotlin.math.sin(Math.toRadians(camera.yaw.toDouble())).toFloat() *
                    kotlin.math.cos(Math.toRadians(camera.pitch.toDouble())).toFloat()
            ).normalize()
            
            // Update audio listener
            audioEngine.listenerPosition.set(position.x, position.y, position.z)
            
            // Set orientation (forward and up vectors)
            audioEngine.listenerOrientation = Pair(
                forward,
                Vector3f(0f, 1f, 0f) // World up
            )
        }
    }
    
    /**
     * Updates all 3D audio source positions based on their GameObject transforms.
     */
    private fun updateAudioSources() {
        scene.gameObjectManager.gameObjects.forEach { gameObject ->
            val audioComponent = gameObject.getComponent<AudioComponent>()
            val transform = gameObject.getComponent<Transform>()
            
            if (audioComponent != null && transform != null && audioComponent.is3D) {
                // Update the audio source position
                audioComponent.updatePosition(transform.translation)
            }
        }
    }
    
    override fun destroy() {
        // Don't destroy audioEngine here - it may be shared
        // Clear scene reference by calling parent destroy
        super.destroy()
    }
}
