package com.pafoid.skate.engine

import com.pafoid.skate.app.SplashScreen
import com.pafoid.skate.editor.EditorWorkspace
import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.ui.handlers.EditorInputHandler
import com.pafoid.skate.engine.core.BootManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EngineState
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.IJobSystem
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

class EngineFixedTimestepTest : KoinTest {

    private lateinit var engine: Engine
    private lateinit var mockSceneManager: SceneManager
    private lateinit var mockRenderer: Renderer
    private lateinit var mockImGuiLayer: ImGuiLayer
    private lateinit var mockEditorWorkspace: EditorWorkspace
    private lateinit var mockLogger: LoggerService
    private lateinit var mockEditorInputHandler: EditorInputHandler
    private lateinit var mockBootManager: BootManager
    private lateinit var mockSplashScreen: SplashScreen
    private lateinit var mockJobSystem: IJobSystem

    @BeforeEach
    fun setup() {
        mockSceneManager = mockk(relaxed = true)
        mockRenderer = mockk(relaxed = true)
        mockImGuiLayer = mockk(relaxed = true)
        mockEditorWorkspace = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        mockEditorInputHandler = mockk(relaxed = true)
        mockBootManager = mockk(relaxed = true)
        mockSplashScreen = mockk(relaxed = true)
        mockJobSystem = mockk(relaxed = true)

        startKoin {
            modules(module {
                single { mockSceneManager }
                single { mockRenderer }
                single { mockImGuiLayer }
                single { mockEditorWorkspace }
                single { mockLogger }
                single { mockEditorInputHandler }
                single { mockBootManager }
                single { mockSplashScreen }
                single { mockJobSystem }
            })
        }

        engine = Engine()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `update should call scene update with actual delta time`() {
        val mockScene = mockk<Scene>(relaxed = true)

        every { mockSceneManager.currentScene } returns mockScene
        // TODO: Fix test - gameViewWindow is now private
        // every { mockImGui.gameViewWindow } returns mockk<GameViewWindow>(relaxed = true)

        engine.engineState.set(EngineState.RUNNING)
        engine.runtimePlaying = true

        val deltaTime = 0.016f // ~60fps

        // Engine now passes the actual delta time to the scene
        // Physics stepping is handled internally by the scene's physics system
        engine.update(deltaTime)

        verify(exactly = 1) { mockScene.update(deltaTime) }
    }

    @Test
    fun `update should pass accumulated time to scene`() {
        val mockScene = mockk<Scene>(relaxed = true)

        every { mockSceneManager.currentScene } returns mockScene
        // TODO: Fix test - gameViewWindow is now private
        // every { mockImGui.gameViewWindow } returns mockk<GameViewWindow>(relaxed = true)

        engine.engineState.set(EngineState.RUNNING)
        engine.runtimePlaying = true

        val deltaTime1 = 0.016f
        val deltaTime2 = 0.032f

        // First update
        engine.update(deltaTime1)
        verify(exactly = 1) { mockScene.update(deltaTime1) }

        // Second update with different delta time
        engine.update(deltaTime2)
        verify(exactly = 1) { mockScene.update(deltaTime2) }
    }
}
