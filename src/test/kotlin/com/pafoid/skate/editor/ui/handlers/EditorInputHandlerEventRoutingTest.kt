package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.InputProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EditorInputHandlerEventRoutingTest {

    @Test
    fun `insert shortcut publishes create empty action instead of mutating scene directly`() {
        val inputProvider = mockk<InputProvider>(relaxed = true)
        val clipboardService = mockk<ClipboardService>(relaxed = true)
        val undoRedoManager = mockk<UndoRedoManager>(relaxed = true)
        val editorInputState = EditorInputState()
        val sceneManager = mockk<SceneManager>()
        val scene = mockk<Scene>(relaxed = true)
        val settingsManager = mockk<EditorSettingsManager>()
        val eventSystem = EventSystem()
        val engine = mockk<Engine>()
        val mappings = EditorInputMappings()

        every { engine.logger } returns mockk(relaxed = true)
        every { engine.eventSystem } returns eventSystem
        every { engine.inputProvider } returns inputProvider
        every { engine.sceneManager } returns sceneManager
        every { settingsManager.editorSettings } returns EditorSettings(editorInputMappings = mappings)
        every { sceneManager.currentScene } returns scene
        every { scene.selectedGameObject } returns null

        every { inputProvider.isControlKeyDown() } returns false
        every { inputProvider.keyBeginPress(any()) } answers { firstArg<Int>() == mappings.hierarchyCreateNew.keyboardKey }

        val handler = EditorInputHandler(
            clipboardService = clipboardService,
            undoRedoManager = undoRedoManager,
            editorInputState = editorInputState,
            engine = engine,
            settingsManager = settingsManager,
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
