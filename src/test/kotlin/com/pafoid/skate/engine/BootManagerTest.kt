package com.pafoid.skate.engine

import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.SplashScreenManager
import com.pafoid.skate.engine.scenes.editor.LevelEditorSceneInitializer
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

class BootManagerTest {

    private val sceneManager = mockk<SceneManager>(relaxed = true)
    private val renderer = mockk<Renderer>(relaxed = true)
    private val logger = mockk<LoggerService>(relaxed = true)
    private val splashScreenManager = mockk<SplashScreenManager>(relaxed = true)
    
    // Use Unconfined dispatcher for testing to avoid JobSystem dependency
    private val bootManager = BootManager(sceneManager, renderer, logger, splashScreenManager, Dispatchers.Unconfined)

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
            sceneManager.changeScene(any<LevelEditorSceneInitializer>(), true)
        }
        
        assertEquals(EngineState.RUNNING, engineState.get())
        verify { splashScreenManager.loadingProgress.set(1.0f) }
    }
}
