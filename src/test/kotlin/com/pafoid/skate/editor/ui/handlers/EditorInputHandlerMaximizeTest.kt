package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.data.ViewportCameraGate
import com.pafoid.skate.editor.data.ViewportFocusReport
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.InputProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

/** EDT-88: Shift+Space maximizes the focused tool window, but not while typing or flying the viewport camera. */
class EditorInputHandlerMaximizeTest {
    private val mappings = EditorInputMappings()
    private val input = mockk<InputProvider>(relaxed = true)
    private val focus = ViewportFocusReport()
    private val events = EventSystem()
    private var textInput = false
    private var shift = true
    private var toggles = 0

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
        every { input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT) } answers { shift }
        every { input.keyBeginPress(any()) } answers { firstArg<Int>() == GLFW.GLFW_KEY_SPACE }
        events.subscribe<WindowAction.ToggleMaximizeFocused> { toggles++ }
        EditorInputHandler(mockk(relaxed = true), mockk(relaxed = true), EditorInputState(), engine, settingsManager, focus) { textInput }
    }

    @Test
    fun `shift space asks to maximize the focused window`() {
        handler.update()
        assertEquals(1, toggles)
        assertEquals("Shift+Space", EditorShortcuts.MAXIMIZE_WINDOW.display(mappings))
    }

    @Test
    fun `space alone does not`() {
        shift = false
        handler.update()
        assertEquals(0, toggles)
    }

    @Test
    fun `not while a text field takes keys`() {
        textInput = true
        handler.update()
        assertEquals(0, toggles)
    }

    @Test
    fun `not while the viewport is focused`() {
        focus.report(focused = true, hovered = true)
        handler.update()
        assertEquals(0, toggles)
    }

    @Test
    fun `the gate`() {
        assertTrue(ViewportCameraGate.maximizeShortcutAllowed(viewportFocused = false, textInputActive = false))
        assertFalse(ViewportCameraGate.maximizeShortcutAllowed(viewportFocused = true, textInputActive = false))
        assertFalse(ViewportCameraGate.maximizeShortcutAllowed(viewportFocused = false, textInputActive = true))
    }

    @Test
    fun `it is rebindable and conflicts with nothing by default`() {
        assertTrue(EditorShortcuts.MAXIMIZE_WINDOW.rebindable)
        assertTrue(EditorShortcuts.conflictsOf(EditorShortcuts.MAXIMIZE_WINDOW, mappings).isEmpty())
    }
}
