package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.EngineState
import com.pafoid.skate.engine.imgui.ImGuiLayer
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.editor.UndoRedoManager
import com.pafoid.skate.engine.utils.serialization.Serializer
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.editor.GameViewWindow
import java.lang.reflect.Field

class SceneManagerFixedTimestepTest : KoinTest {

    @BeforeEach
    fun setup() {
        startKoin {
            modules(module {
                single { mockk<ResourceManager>(relaxed = true) }
                single { mockk<KeyListener>(relaxed = true) }
                single { mockk<LoggerService>(relaxed = true) }
                single { mockk<ClipboardService>(relaxed = true) }
                single { mockk<Serializer>(relaxed = true) }
                single { mockk<UndoRedoManager>(relaxed = true) }
            })
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `draw should call scene update with fixed timestep`() {
        val sceneManager = SceneManager()
        val mockScene = mockk<Scene>(relaxed = true)
        val mockImGui = mockk<ImGuiLayer>(relaxed = true)
        val mockRenderer = mockk<Renderer>(relaxed = true)
        
        // Inject mock renderer using reflection because it's private/lateinit and created internally
        val rendererField: Field = SceneManager::class.java.getDeclaredField("renderer")
        rendererField.isAccessible = true
        rendererField.set(sceneManager, mockRenderer)

        sceneManager.currentScene = mockScene
        sceneManager.runtimePlaying = true
        sceneManager.engineState.set(EngineState.RUNNING)
        
        every { mockImGui.gameViewWindow } returns mockk<GameViewWindow>(relaxed = true)

        // Simulate 1 second of frame time in one big chunk (should result in 60 updates)
        // FIXED_TIME_STEP is 1/60 (~0.0166667)
        // However, max time step is 0.25.
        // So if we pass 1.0, it clamps to 0.25.
        // 0.25 / (1/60) = 15 updates.
        sceneManager.draw(1.0f, mockImGui)

        verify(exactly = 15) { mockScene.update(1.0f / 60.0f) }
    }

    @Test
    fun `draw should accumulate small deltas and trigger update`() {
        val sceneManager = SceneManager()
        val mockScene = mockk<Scene>(relaxed = true)
        val mockImGui = mockk<ImGuiLayer>(relaxed = true)
        val mockRenderer = mockk<Renderer>(relaxed = true)
        
        val rendererField: Field = SceneManager::class.java.getDeclaredField("renderer")
        rendererField.isAccessible = true
        rendererField.set(sceneManager, mockRenderer)

        sceneManager.currentScene = mockScene
        sceneManager.runtimePlaying = true
        sceneManager.engineState.set(EngineState.RUNNING)
        every { mockImGui.gameViewWindow } returns mockk<GameViewWindow>(relaxed = true)

        val step = 1.0f / 60.0f
        val halfStep = step / 2.0f

        // 1. Pass half step -> Accumulator = 0.5 step. No update.
        sceneManager.draw(halfStep, mockImGui)
        verify(exactly = 0) { mockScene.update(any()) }

        // 2. Pass another half step -> Accumulator = 1.0 step. 1 update.
        sceneManager.draw(halfStep, mockImGui)
        verify(exactly = 1) { mockScene.update(step) }
    }
}
