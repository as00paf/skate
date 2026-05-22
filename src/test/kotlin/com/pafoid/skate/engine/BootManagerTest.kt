package com.pafoid.skate.engine

import com.pafoid.skate.app.SplashScreen
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.core.BootManager
import com.pafoid.skate.engine.core.EngineState
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.Physics3DFactory
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.Renderer
import io.mockk.every
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.test.KoinTest
import java.util.concurrent.atomic.AtomicReference

class BootManagerTest : KoinTest {

    private val sceneManager = mockk<SceneManager>(relaxed = true)
    private val renderer = mockk<Renderer>(relaxed = true)
    private val mockLogger = mockk<LoggerService>(relaxed = true)
    private val splashScreen = mockk<SplashScreen>(relaxed = true)
    private val audioEngine = mockk<AudioEngine>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>(relaxed = true)
    private val settingsManager = mockk<SettingsManager>(relaxed = true)
    private val physics3DFactory = mockk<Physics3DFactory>(relaxed = true)
    private val nativeLibraryLoader = mockk<NativeLibraryLoader>(relaxed = true)

    private val bootManager =
        BootManager(sceneManager, renderer, mockLogger, splashScreen, audioEngine, resourceManager, settingsManager, physics3DFactory, nativeLibraryLoader)

    @BeforeEach
    fun setup() {
        every { physics3DFactory.create() } returns mockk<IPhysics3D>(relaxed = true)
        stopKoin()
        startKoin {
            printLogger(Level.ERROR)
            modules(module {
                single<LoggerService> { mockLogger }
                single<BootManager> { bootManager }
                single<Renderer> { renderer }
                single<SceneManager> { sceneManager }
                single<SplashScreen> { splashScreen }
                single<AudioEngine> { audioEngine }
                single<ResourceManager> { resourceManager }
            })
        }
    }

    @AfterEach
    fun teardown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun `boot sequence initializes systems and transitions to RUNNING`() = runTest(StandardTestDispatcher()) {
        val engineState = AtomicReference(EngineState.BOOTING)

        coEvery { splashScreen.init() } just Runs
        coEvery { renderer.initialize() } just Runs
        coEvery { resourceManager.loadModel(any()) } returns mockk(relaxed = true)
        every { nativeLibraryLoader.loadNativeLibrary() } just Runs

        bootManager.boot(engineState)

        coVerify {
            splashScreen.init()
            renderer.initialize()
            sceneManager.openScene(any<Scene>(), true)
        }
        verify(exactly = 1) { nativeLibraryLoader.loadNativeLibrary() }

        assertEquals(EngineState.RUNNING, engineState.get())
        verify { splashScreen.loadingProgress.set(1.0f) }
    }
}


