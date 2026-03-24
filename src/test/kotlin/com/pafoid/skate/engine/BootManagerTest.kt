package com.pafoid.skate.engine

import com.pafoid.skate.app.SplashScreen
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.core.BootManager
import com.pafoid.skate.engine.core.Engine
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
    private val resourceManager = mockk<ResourceManager>(relaxed = true)
    private val undoRedoManager = mockk<UndoRedoManager>(relaxed = true)
    private val engine = mockk<Engine>(relaxed = true)
    private val settingsManager = mockk<SettingsManager>(relaxed = true)
    private val prefabsGenerator = mockk<PrefabsGenerator>(relaxed = true)
    private val audioEngine = mockk<AudioEngine>(relaxed = true)

    private val stringManager = mockk<com.pafoid.skate.editor.systems.StringManager>(relaxed = true)
    private val keyListener = mockk<com.pafoid.skate.engine.input.listeners.KeyListener>(relaxed = true)
    private val mouseListener = mockk<com.pafoid.skate.engine.input.listeners.MouseListener>(relaxed = true)
    private val serializer = mockk<com.pafoid.skate.engine.assets.serialization.Serializer>(relaxed = true)
    private val debugRenderer = mockk<com.pafoid.skate.engine.render.renderer.DebugRenderer>(relaxed = true)
    private val inputProvider = mockk<com.pafoid.skate.engine.input.IInputProvider>(relaxed = true)

    // Use Unconfined dispatcher for testing to avoid JobSystem dependency
    private val bootManager =
        BootManager(sceneManager, renderer, mockLogger, splashScreen, audioEngine, Dispatchers.Unconfined)

    @BeforeEach
    fun setup() {
        startKoin {
            modules(module {
                single<LoggerService> { mockLogger }
                single<BootManager> { bootManager }
                single<Renderer> { renderer }
                single<ResourceManager> { resourceManager }
                single<SceneManager> { sceneManager }
                single<SplashScreen> { splashScreen }
                single<UndoRedoManager> { undoRedoManager }
                single<Engine> { engine }
                single<SettingsManager> { settingsManager }
                single<PrefabsGenerator> { prefabsGenerator }
                single<AudioEngine> { audioEngine }
                single<com.pafoid.skate.editor.systems.StringManager> { stringManager }
                single<com.pafoid.skate.engine.input.listeners.KeyListener> { keyListener }
                single<com.pafoid.skate.engine.input.listeners.MouseListener> { mouseListener }
                single<com.pafoid.skate.engine.assets.serialization.Serializer> { serializer }
                single<com.pafoid.skate.engine.render.renderer.DebugRenderer> { debugRenderer }
                single<com.pafoid.skate.engine.input.IInputProvider> { inputProvider }
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

        // Mock behaviors
        coEvery { splashScreen.init() } just Runs
        coEvery { renderer.initialize() } just Runs

        bootManager.boot(engineState)

        // Verifications
        coVerify {
            splashScreen.init()
            renderer.initialize()
            sceneManager.changeScene(any<Scene>(), true)
        }

        assertEquals(EngineState.RUNNING, engineState.get())
        verify { splashScreen.loadingProgress.set(1.0f) }
    }
}
