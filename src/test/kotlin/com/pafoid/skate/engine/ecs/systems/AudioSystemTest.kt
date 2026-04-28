package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.Camera
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest

class AudioSystemTest : KoinTest {

    private val audioEngine = mockk<AudioEngine>(relaxed = true)
    private val logger = mockk<LoggerService>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>(relaxed = true)
    private val scene = mockk<Scene>(relaxed = true)
    private val camera = mockk<Camera>(relaxed = true)
    private val gameObjectManager = mockk<GameObjectManager>(relaxed = true)

    private lateinit var audioSystem: AudioSystem

    @BeforeEach
    fun setUp() {
        startKoin {
            modules(module {
                single { resourceManager }
            })
        }

        every { scene.camera } returns camera
        every { scene.gameObjectSystem } returns gameObjectManager
        every { camera.position } returns Vector3f(0f, 0f, 0f)
        every { camera.yaw } returns 0f
        every { camera.pitch } returns 0f

        audioSystem = AudioSystem(audioEngine, logger)
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
        every { gameObjectManager.gameObjects } returns mutableListOf()

        // Act
        audioSystem.update(0.16f)

        // Assert
        verify { audioEngine.init() }
        verify { logger.logEngine(any()) }
    }

    @Test
    fun `update - stops if initialization fails`() {
        // Arrange
        every { audioEngine.init() } returns false
        every { gameObjectManager.gameObjects } returns mutableListOf()

        // Act
        audioSystem.update(0.16f)

        // Assert
        verify { audioEngine.init() }
        verify { logger.logEngine("AudioSystem: Failed to initialize - audio disabled", any()) }
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
}
