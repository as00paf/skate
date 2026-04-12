package com.pafoid.skate.engine

import com.pafoid.skate.app.SplashScreen
import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.core.BootManager
import com.pafoid.skate.engine.core.EngineState
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.renderer.Renderer
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.concurrent.atomic.AtomicReference

class BootManagerTest : KoinTest {

    private val sceneManager = mockk<SceneManager>(relaxed = true)
    private val renderer = mockk<Renderer>(relaxed = true)
    private val mockLogger = mockk<LoggerService>(relaxed = true)
    private val splashScreen = mockk<SplashScreen>(relaxed = true)
    private val audioEngine = mockk<AudioEngine>(relaxed = true)
    private val sceneInitializer = mockk<LevelEditorSceneInitializer>(relaxed = true)

    private val bootManager =
        BootManager(sceneManager, renderer, mockLogger, splashScreen, audioEngine, sceneInitializer, Dispatchers.Unconfined)

    @BeforeEach
    fun setup() {
        startKoin {
            modules(module {
                single<LoggerService> { mockLogger }
                single<BootManager> { bootManager }
                single<Renderer> { renderer }
                single<SceneManager> { sceneManager }
                single<SplashScreen> { splashScreen }
                single<AudioEngine> { audioEngine }
                single<LevelEditorSceneInitializer> { sceneInitializer }
            })
        }
    }

    @AfterEach
    fun teardown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun `boot sequence initializes systems and transitions to RUNNING`() = runBlocking {
        val engineState = AtomicReference(EngineState.BOOTING)

        coEvery { splashScreen.init() } just Runs
        coEvery { renderer.initialize() } just Runs

        bootManager.boot(engineState)

        coVerify {
            splashScreen.init()
            renderer.initialize()
            sceneManager.openScene(any<Scene>(), true)
        }

        assertEquals(EngineState.RUNNING, engineState.get())
        verify { splashScreen.loadingProgress.set(1.0f) }
    }
}
