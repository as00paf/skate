package com.pafoid.skate.engine.ecs.components

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for AudioComponent.
 */
class AudioComponentTest {

    private lateinit var audioComponent: AudioComponent

    @BeforeEach
    fun setUp() {
        audioComponent = AudioComponent()
    }

    @Test
    fun `default values - empty path, 3D enabled, no loop, full volume`() {
        // Assert
        assertEquals("", audioComponent.soundFilePath)
        assertTrue(audioComponent.is3D)
        assertFalse(audioComponent.loops)
        assertEquals(1.0f, audioComponent.volume, 0.001f)
    }

    @Test
    fun `constructor - accepts custom values`() {
        // Arrange
        audioComponent = AudioComponent(
            soundFilePath = "test.ogg",
            is3D = false,
            loops = true,
            volume = 0.5f
        )
        
        // Assert
        assertEquals("test.ogg", audioComponent.soundFilePath)
        assertFalse(audioComponent.is3D)
        assertTrue(audioComponent.loops)
        assertEquals(0.5f, audioComponent.volume, 0.001f)
    }

    @Test
    fun `applyVolume - clamps values above 1`() {
        // Act
        audioComponent.applyVolume(1.5f)
        
        // Assert
        assertEquals(1.0f, audioComponent.volume, 0.001f)
    }

    @Test
    fun `applyVolume - clamps values below 0`() {
        // Act
        audioComponent.applyVolume(-0.5f)
        
        // Assert
        assertEquals(0.0f, audioComponent.volume, 0.001f)
    }

    @Test
    fun `applyVolume - accepts valid range`() {
        // Act
        audioComponent.applyVolume(0.75f)
        
        // Assert
        assertEquals(0.75f, audioComponent.volume, 0.001f)
    }

    @Test
    fun `apply3D - toggles 3D mode`() {
        // Arrange
        assertTrue(audioComponent.is3D)
        
        // Act
        audioComponent.apply3D(false)
        
        // Assert
        assertFalse(audioComponent.is3D)
    }

    @Test
    fun `applyLooping - toggles looping state`() {
        // Arrange
        assertFalse(audioComponent.loops)
        
        // Act
        audioComponent.applyLooping(true)
        
        // Assert
        assertTrue(audioComponent.loops)
    }

    @Test
    fun `isPlaying - returns false when no sound loaded`() {
        // Assert
        assertFalse(audioComponent.isPlaying())
    }

    @Test
    fun `destroy - cleans up without error when no sound loaded`() {
        // Act & Assert - should not throw
        audioComponent.destroy()
        assertFalse(audioComponent.isPlaying())
    }
}
