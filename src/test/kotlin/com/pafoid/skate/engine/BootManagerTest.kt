package com.pafoid.skate.engine

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.editor.UndoRedoManager
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.physics3d.SkateboardPhysicsTest.Companion.engine
import com.pafoid.skate.engine.prefabs.PrefabsGenerator
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SplashScreenManager
import com.pafoid.skate.engine.utils.SettingsManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
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
    private val logger = mockk<LoggerService>(relaxed = true)
    private val splashScreenManager = mockk<SplashScreenManager>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>(relaxed = true)
    private val mouseListener = mockk<MouseListener>(relaxed = true)
    private val undoRedoManager = mockk<UndoRedoManager>(relaxed = true)
    private val debugDraw = mockk<DebugDraw>(relaxed = true)
    private val engine = mockk<Engine>(relaxed = true)
    private val settingsManager = mockk<SettingsManager>(relaxed = true)
    private val prefabsGenerator = mockk<PrefabsGenerator>(relaxed = true)

    // Use Unconfined dispatcher for testing to avoid JobSystem dependency
    private val bootManager = BootManager(sceneManager, renderer, logger, splashScreenManager, Dispatchers.Unconfined)


    @BeforeEach
    fun setup() {
        startKoin {
            modules(module {
                single { logger }
                single { bootManager }
                single { renderer }
                single { resourceManager }
                single { mouseListener }
                single { sceneManager }
                single { splashScreenManager }
                single { undoRedoManager }
                single { debugDraw }
                single { engine }
                single { settingsManager }
                single { prefabsGenerator }
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
        coEvery { splashScreenManager.init() } just Runs
        coEvery { renderer.initFrameBuffer() } just Runs

        bootManager.boot(engineState)

        // Verifications
        coVerify {
            splashScreenManager.init()
            renderer.initFrameBuffer()
            renderer.loadShaders(any())
            sceneManager.changeScene(any<Scene>(), true)
        }
        
        assertEquals(EngineState.RUNNING, engineState.get())
        verify { splashScreenManager.loadingProgress.set(1.0f) }
    }
}
