package com.pafoid.skate.engine

import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.core.BootManager
import com.pafoid.skate.engine.core.EngineState
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import io.mockk.Runs
import io.mockk.every
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

    private val mockLogger = mockk<LoggerService>(relaxed = true)
    private val audioEngine = mockk<AudioEngine>(relaxed = true)
    private val settingsManager = mockk<SettingsManager>(relaxed = true)
    private val nativeLibraryLoader = mockk<NativeLibraryLoader>(relaxed = true)

    private val bootManager = BootManager(mockLogger, audioEngine, settingsManager, nativeLibraryLoader)

    @BeforeEach
    fun setup() {
        stopKoin()
        startKoin {
            printLogger(Level.ERROR)
            modules(module {
                single<LoggerService> { mockLogger }
                single<BootManager> { bootManager }
                single<AudioEngine> { audioEngine }
                single<SettingsManager> { settingsManager }
                single<NativeLibraryLoader> { nativeLibraryLoader }
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

        every { nativeLibraryLoader.loadNativeLibrary() } just Runs
        every { audioEngine.init() } returns true
        every { settingsManager.load() } just Runs

        bootManager.boot(engineState)

        verify(exactly = 1) { nativeLibraryLoader.loadNativeLibrary() }
        verify(exactly = 1) { audioEngine.init() }
        verify(exactly = 1) { settingsManager.load() }

        assertEquals(EngineState.RUNNING, engineState.get())
    }
}