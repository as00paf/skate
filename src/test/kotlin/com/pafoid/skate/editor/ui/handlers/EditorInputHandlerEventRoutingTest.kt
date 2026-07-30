package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.listeners.GamepadListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class EditorInputHandlerEventRoutingTest {

    @Test
    fun `insert shortcut publishes create empty action instead of mutating scene directly`() {
        val inputProvider = mockk<InputProvider>()
        val mouseListener = mockk<MouseListener>()
        val gamepadListener = mockk<GamepadListener>()
        val clipboardService = mockk<ClipboardService>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>(relaxed = true)
        val editorInputState = EditorInputState()
        val sceneManager = mockk<SceneManager>()
        val scene = mockk<Scene>(relaxed = true)
        val settingsManager = mockk<EditorSettingsManager>()
        val eventSystem = EventSystem()
        val engine = mockk<Engine>()

        every { engine.runtimePlaying } returns false
        every { settingsManager.loadInputMappings() } returns InputMappings()
        every { sceneManager.currentScene } returns scene
        every { scene.selectedGameObject } returns null

        every { inputProvider.isKeyPressed(any()) } returns false
        every { inputProvider.keyBeginPress(any()) } answers { firstArg<Int>() == GLFW.GLFW_KEY_INSERT }
        every { inputProvider.endFrame() } returns Unit

        every { mouseListener.isInsideViewport() } returns false
        every { mouseListener.dx } returns 0f
        every { mouseListener.dy } returns 0f
        every { mouseListener.isMouseButtonDown(any(), any()) } returns false
        every { mouseListener.mouseButtonBeginPress(any()) } returns false
        every { mouseListener.getScrollY() } returns 0f
        every { mouseListener.getX() } returns 0f
        every { mouseListener.getY() } returns 0f

        every { gamepadListener.getAxes(any()) } returns null

        val handler = EditorInputHandler(
            clipboardService = clipboardService,
            undoRedoManager = undoRedoManager,
            editorInputState = editorInputState,
            engine = engine,
            settingsManager = mockk(),
        )

        var createEvents = 0
        eventSystem.subscribe<ViewportAction.CreateEmpty> { event ->
            createEvents++
            assertEquals(scene, event.scene)
        }

        handler.update()

        assertEquals(1, createEvents)
        verify(exactly = 0) { undoRedoManager.executeCommand(any()) }
    }
}
