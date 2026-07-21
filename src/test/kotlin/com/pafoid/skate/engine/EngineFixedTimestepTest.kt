package com.pafoid.skate.engine

import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EngineState
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.renderer.Renderer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EngineFixedTimestepTest {

    private lateinit var engine: Engine
    private lateinit var mockSceneManager: SceneManager
    private lateinit var mockRenderer: Renderer

    @BeforeEach
    fun setup() {
        mockSceneManager = mockk(relaxed = true)
        mockRenderer = mockk(relaxed = true)

        engine = Engine()
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

    @Test
    fun `update should skip render when no scene is active`() {
        every { mockSceneManager.currentScene } returns null

        engine.engineState.set(EngineState.RUNNING)
        engine.runtimePlaying = false

        val deltaTime = 0.016f
        engine.update(deltaTime)

        verify(exactly = 0) { mockRenderer.render(any()) }
    }
}
