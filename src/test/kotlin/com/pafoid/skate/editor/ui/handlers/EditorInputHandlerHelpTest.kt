package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.data.ViewportFocusReport
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.InputBinding
import com.pafoid.skate.engine.input.InputProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

/** EDT-101: F1 asks for the focused window's help; rebindable; ignored while typing. */
class EditorInputHandlerHelpTest {
    private val mappings = EditorInputMappings()
    private val input = mockk<InputProvider>(relaxed = true)
    private val events = EventSystem()
    private var textInput = false
    private var key = GLFW.GLFW_KEY_F1
    private var asked = 0

    private val handler: EditorInputHandler = run {
        val engine = mockk<Engine>()
        val sceneManager = mockk<SceneManager>()
        val settingsManager = mockk<EditorSettingsManager>()
        every { engine.logger } returns mockk(relaxed = true)
        every { engine.eventSystem } returns events
        every { engine.inputProvider } returns input
        every { engine.sceneManager } returns sceneManager
        every { sceneManager.currentScene } returns null
        every { settingsManager.editorSettings } returns EditorSettings(editorInputMappings = mappings)
        every { input.isControlKeyDown() } returns false
        every { input.keyBeginPress(any()) } answers { firstArg<Int>() == key }
        events.subscribe<WindowAction.HelpForFocused> { asked++ }
        EditorInputHandler(mockk(relaxed = true), mockk(relaxed = true), EditorInputState(), engine, settingsManager, ViewportFocusReport()) { textInput }
    }

    @Test
    fun `f1 asks for help`() {
        handler.update()
        assertEquals(1, asked)
        assertEquals("F1", EditorShortcuts.HELP.display(mappings))
    }

    @Test
    fun `not while typing`() {
        textInput = true
        handler.update()
        assertEquals(0, asked)
    }

    @Test
    fun `it can be rebound and conflicts with nothing by default`() {
        assertTrue(EditorShortcuts.HELP.rebindable)
        assertTrue(EditorShortcuts.conflictsOf(EditorShortcuts.HELP, mappings).isEmpty())
        EditorShortcuts.HELP.binding!!.set(mappings, InputBinding(keyboardKey = GLFW.GLFW_KEY_F2))
        key = GLFW.GLFW_KEY_F2
        handler.update()
        assertEquals(1, asked)
    }
}
