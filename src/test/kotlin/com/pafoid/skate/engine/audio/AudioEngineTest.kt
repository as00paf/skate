package com.pafoid.skate.engine.audio

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for AudioEngine.
 * 
 * Note: These tests verify the API and logic without requiring actual OpenAL hardware.
 * Hardware-dependent tests should be marked as @Disabled for CI environments.
 */
class AudioEngineTest {

    private lateinit var audioEngine: AudioEngine

    @BeforeEach
    fun setUp() {
        audioEngine = AudioEngine()
    }

    @Test
    fun `initial state - not initialized`() {
        // Assert
        assertFalse(audioEngine.isCapable())
        assertEquals(1.0f, audioEngine.masterVolume, 0.001f)
        assertEquals(0f, audioEngine.listenerPosition.x, 0.001f)
        assertEquals(0f, audioEngine.listenerPosition.y, 0.001f)
        assertEquals(0f, audioEngine.listenerPosition.z, 0.001f)
    }

    @Test
    fun `masterVolume - accepts values between 0 and 1`() {
        // Act
        audioEngine.masterVolume = 0.5f
        
        // Assert
        assertEquals(0.5f, audioEngine.masterVolume, 0.001f)
    }

    @Test
    fun `masterVolume - clamps values above 1`() {
        // Act
        audioEngine.masterVolume = 1.5f
        
        // Assert
        assertEquals(1.0f, audioEngine.masterVolume, 0.001f)
    }

    @Test
    fun `masterVolume - clamps values below 0`() {
        // Act
        audioEngine.masterVolume = -0.5f
        
        // Assert
        assertEquals(0.0f, audioEngine.masterVolume, 0.001f)
    }

    @Test
    fun `listenerPosition - updates position vector`() {
        // Arrange
        val newPosition = Vector3f(10f, 5f, -20f)
        
        // Act
        audioEngine.listenerPosition = newPosition
        
        // Assert
        assertEquals(10f, audioEngine.listenerPosition.x, 0.001f)
        assertEquals(5f, audioEngine.listenerPosition.y, 0.001f)
        assertEquals(-20f, audioEngine.listenerPosition.z, 0.001f)
    }

    @Test
    fun `listenerVelocity - updates velocity vector`() {
        // Arrange
        val newVelocity = Vector3f(1f, 0f, 0f)
        
        // Act
        audioEngine.listenerVelocity = newVelocity
        
        // Assert
        assertEquals(1f, audioEngine.listenerVelocity.x, 0.001f)
        assertEquals(0f, audioEngine.listenerVelocity.y, 0.001f)
        assertEquals(0f, audioEngine.listenerVelocity.z, 0.001f)
    }

    @Test
    fun `listenerOrientation - default orientation is forward negative Z and up Y`() {
        // Assert
        val (forward, up) = audioEngine.listenerOrientation
        
        assertEquals(0f, forward.x, 0.001f)
        assertEquals(0f, forward.y, 0.001f)
        assertEquals(-1f, forward.z, 0.001f)
        
        assertEquals(0f, up.x, 0.001f)
        assertEquals(1f, up.y, 0.001f)
        assertEquals(0f, up.z, 0.001f)
    }

    @Test
    fun `listenerOrientation - updates orientation vectors`() {
        // Arrange
        val newForward = Vector3f(0f, 0f, 1f)
        val newUp = Vector3f(0f, -1f, 0f)
        
        // Act
        audioEngine.listenerOrientation = Pair(newForward, newUp)
        
        // Assert
        assertEquals(0f, audioEngine.listenerOrientation.first.x, 0.001f)
        assertEquals(0f, audioEngine.listenerOrientation.first.y, 0.001f)
        assertEquals(1f, audioEngine.listenerOrientation.first.z, 0.001f)
        
        assertEquals(0f, audioEngine.listenerOrientation.second.x, 0.001f)
        assertEquals(-1f, audioEngine.listenerOrientation.second.y, 0.001f)
        assertEquals(0f, audioEngine.listenerOrientation.second.z, 0.001f)
    }

    @Test
    fun `destroy - can be called without initialization`() {
        // Act & Assert - should not throw
        audioEngine.destroy()
        assertFalse(audioEngine.isCapable())
    }
}
