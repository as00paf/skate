package com.pafoid.skate.engine.audio

import com.pafoid.skate.engine.core.LoggerService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for AudioEngine.
 */
class AudioEngineTest {

    private lateinit var audioEngine: AudioEngine
    private val logger = mockk<LoggerService>(relaxed = true)

    @BeforeEach
    fun setUp() {
        audioEngine = AudioEngine(logger)
    }

    @Test
    fun `initial state - not initialized`() {
        // Assert
        assertFalse(audioEngine.isInitialized)
        assertEquals(0L, audioEngine.getContext())
        assertEquals(0L, audioEngine.getDevice())
    }

    @Test
    fun `methods do not crash when not initialized`() {
        assertDoesNotThrow {
            audioEngine.setMasterVolume(0.5f)
            audioEngine.setListenerPosition(10f, 5f, -20f)
            audioEngine.setListenerVelocity(1f, 0f, 0f)
            audioEngine.setListenerOrientation(floatArrayOf(0f, 0f, -1f), floatArrayOf(0f, 1f, 0f))
        }
    }

    @Test
    fun `destroy - can be called without initialization`() {
        // Act & Assert - should not throw
        assertDoesNotThrow {
            audioEngine.destroy()
        }
        assertFalse(audioEngine.isInitialized)
    }
}
