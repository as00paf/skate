package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.IInputBuffer
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.listeners.GamepadListener
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.lwjgl.glfw.GLFW

class EditorInputHandlerEventRoutingTest {

    @AfterEach
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun `insert shortcut publishes create empty action instead of mutating scene directly`() {
        val keyListener = mockk<KeyListener>()
        val mouseListener = mockk<MouseListener>()
        val gamepadListener = mockk<GamepadListener>()
        val inputBuffer = mockk<IInputBuffer>(relaxed = true)
        val clipboardService = mockk<ClipboardService>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        val editorInputState = EditorInputState()
        val sceneManager = mockk<SceneManager>()
        val scene = mockk<Scene>(relaxed = true)
        val settingsManager = mockk<SettingsManager>()
        val eventSystem = EventSystem()
        val engine = mockk<Engine>()

        every { engine.runtimePlaying } returns false
        every { settingsManager.loadInputMappings() } returns InputMappings()
        every { sceneManager.currentScene } returns scene
        every { scene.selectedGameObject } returns null

        every { keyListener.isKeyPressed(any()) } returns false
        every { keyListener.keyBeginPress(any()) } answers { firstArg<Int>() == GLFW.GLFW_KEY_INSERT }
        every { keyListener.endFrame() } returns Unit

        every { mouseListener.isInsideViewport() } returns false
        every { mouseListener.getDx() } returns 0f
        every { mouseListener.getDy() } returns 0f
        every { mouseListener.isMouseButtonDown(any(), any()) } returns false
        every { mouseListener.mouseButtonBeginPress(any()) } returns false
        every { mouseListener.getScrollY() } returns 0f
        every { mouseListener.getX() } returns 0f
        every { mouseListener.getY() } returns 0f
        every { mouseListener.endFrame() } returns Unit

        every { gamepadListener.getAxes(any()) } returns null

        runCatching { stopKoin() }
        startKoin {
            modules(
                module {
                    single { settingsManager }
                    single { eventSystem }
                    single { engine }
                }
            )
        }

        val handler = EditorInputHandler(
            keyListener = keyListener,
            mouseListener = mouseListener,
            joystickListener = gamepadListener,
            inputBuffer = inputBuffer,
            clipboardService = clipboardService,
            undoRedoManager = undoRedoManager,
            logger = logger,
            editorInputState = editorInputState,
            sceneManager = sceneManager,
            engine = engine
        )

        var createEvents = 0
        eventSystem.subscribe<ViewportAction.CreateEmpty> { event ->
            createEvents++
            assertEquals(scene, event.scene)
        }

        handler.update(1f / 60f)

        assertEquals(1, createEvents)
        verify(exactly = 0) { undoRedoManager.executeCommand(any()) }
    }
}
