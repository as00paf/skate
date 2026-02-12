package com.pafoid.skate.engine

import com.pafoid.skate.engine.editor.EditorInputHandler
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.imgui.ImGuiLayer
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.editor.LevelEditorSceneInitializer
import com.pafoid.skate.engine.scenes.getSelectedGameObject
import com.pafoid.skate.engine.scenes.getGameObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import com.pafoid.skate.engine.editor.GameViewWindow

class EngineFixedTimestepTest : KoinTest {

    private lateinit var engine: Engine
    private lateinit var mockSceneManager: SceneManager
    private lateinit var mockRenderer: Renderer
    private lateinit var mockLogger: LoggerService
    private lateinit var mockEditorInputHandler: EditorInputHandler

    @BeforeEach
    fun setup() {
        mockSceneManager = mockk(relaxed = true)
        mockRenderer = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockEditorInputHandler = mockk(relaxed = true)

        startKoin {
            modules(module {
                single { mockSceneManager }
                single { mockRenderer }
                single { mockLogger }
                single { mockEditorInputHandler }
            })
        }
        
        engine = Engine()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `update should call scene update with fixed timestep`() {
        val mockScene = mockk<Scene>(relaxed = true)
        val mockImGui = mockk<ImGuiLayer>(relaxed = true)
        
        every { mockSceneManager.currentScene } returns mockScene
        every { mockImGui.gameViewWindow } returns mockk<GameViewWindow>(relaxed = true)

        engine.engineState.set(EngineState.RUNNING)
        engine.runtimePlaying = true

        // Simulate 1 second of frame time in one big chunk (should result in 15 updates due to clamping)
        // FIXED_TIME_STEP is 1/60 (~0.0166667)
        // Max time step is 0.25.
        // 0.25 / (1/60) = 15 updates.
        engine.update(1.0f, mockImGui)

        verify(exactly = 15) { mockScene.update(1.0f / 60.0f) }
    }

    @Test
    fun `update should accumulate small deltas and trigger update`() {
        val mockScene = mockk<Scene>(relaxed = true)
        val mockImGui = mockk<ImGuiLayer>(relaxed = true)
        
        every { mockSceneManager.currentScene } returns mockScene
        every { mockImGui.gameViewWindow } returns mockk<GameViewWindow>(relaxed = true)

        engine.engineState.set(EngineState.RUNNING)
        engine.runtimePlaying = true

        val step = 1.0f / 60.0f
        val halfStep = step / 2.0f

        // 1. Pass half step -> Accumulator = 0.5 step. No update.
        engine.update(halfStep, mockImGui)
        verify(exactly = 0) { mockScene.update(any()) }

        // 2. Pass another half step -> Accumulator = 1.0 step. 1 update.
        engine.update(halfStep, mockImGui)
        verify(exactly = 1) { mockScene.update(step) }
    }
}
