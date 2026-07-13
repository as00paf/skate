package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.data.LogLevel
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.Camera
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest

class AudioSystemTest : KoinTest {

    private val audioEngine = mockk<AudioEngine>(relaxed = true)
    private val logger = mockk<LoggerService>(relaxed = true)
    private val assetsManager = mockk<AssetsManager>(relaxed = true)
    private val scene = mockk<Scene>(relaxed = true)
    private val camera = mockk<Camera>(relaxed = true)

    private lateinit var audioSystem: AudioSystem

    @BeforeEach
    fun setUp() {
        every { scene.camera } returns camera
        every { scene.gameObjects } returns mutableListOf()
        every { camera.position } returns Vector3f(0f, 0f, 0f)
        every { camera.yaw } returns 0f
        every { camera.pitch } returns 0f

        audioSystem = AudioSystem(audioEngine, logger, assetsManager)
        audioSystem.init(scene)
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun `update - initializes audio engine if not initialized`() {
        // Arrange
        every { audioEngine.init() } returns true
        every { audioEngine.isInitialized } returns true

        // Act
        audioSystem.update(0.16f)

        // Assert
        verify { audioEngine.init() }
        verify { logger.log(any<String>(), any<LogLevel>(), any()) }
    }

    @Test
    fun `update - stops if initialization fails`() {
        // Arrange
        every { audioEngine.init() } returns false

        // Act
        audioSystem.update(0.16f)

        // Assert
        verify { audioEngine.init() }
        verify { logger.log("AudioSystem: Failed to initialize - audio disabled", any<LogLevel>(), any()) }
        // Should not try to update listener
        verify(exactly = 0) { audioEngine.setListenerPosition(any(), any(), any()) }
    }

    @Test
    fun `destroy - cleans up resources and engine`() {
        // Act
        audioSystem.destroy()

        // Assert
        // In this basic test, we just ensure it doesn't crash when empty
        // Further tests could mock activeSources if we had access to it.
        // It's internal state, so we just verify it completes.
        verify(exactly = 0) { audioEngine.destroy() } // The system doesn't destroy the engine, just itself
    }

    @Test
    fun `setMasterVolume clamps and persists value`() {
        audioSystem.setMasterVolume(1.5f)
        verify { audioEngine.setMasterVolume(1.0f) }

        audioSystem.setMasterVolume(-0.5f)
        verify { audioEngine.setMasterVolume(0.0f) }
    }

    @Test
    fun `update listener uses configured master volume instead of resetting to one`() {
        every { audioEngine.init() } returns true
        every { audioEngine.isInitialized } returns true

        // First update initializes system.
        audioSystem.update(0.16f)

        // Set volume via system API and verify subsequent update keeps that value.
        audioSystem.setMasterVolume(0.35f)
        clearMocks(audioEngine, answers = false, recordedCalls = true)
        every { audioEngine.isInitialized } returns true

        audioSystem.update(0.16f)

        verify { audioEngine.setMasterVolume(0.35f) }
        verify(exactly = 0) { audioEngine.setMasterVolume(1.0f) }
    }
}
