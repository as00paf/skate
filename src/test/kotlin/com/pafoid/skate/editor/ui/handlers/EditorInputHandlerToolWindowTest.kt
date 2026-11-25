package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.data.EditorInputState
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
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

/** EDT-84 Q6: Alt+<key> toggles a tool window, but not while typing, and not without Alt. */
class EditorInputHandlerToolWindowTest {
    private val input = mockk<InputProvider>(relaxed = true)
    private val events = EventSystem()
    private var textInput = false
    private var alt = true
    private var key = GLFW.GLFW_KEY_4
    private val toggled = mutableListOf<String>()

    private val handler: EditorInputHandler = run {
        val engine = mockk<Engine>()
        val sceneManager = mockk<SceneManager>()
        val settingsManager = mockk<EditorSettingsManager>()
        every { engine.logger } returns mockk(relaxed = true)
        every { engine.eventSystem } returns events
        every { engine.inputProvider } returns input
        every { engine.sceneManager } returns sceneManager
        every { sceneManager.currentScene } returns null
        every { settingsManager.editorSettings } returns EditorSettings(editorInputMappings = EditorInputMappings())
        every { input.isControlKeyDown() } returns false
        every { input.isKeyPressed(GLFW.GLFW_KEY_LEFT_ALT) } answers { alt }
        every { input.keyBeginPress(any()) } answers { firstArg<Int>() == key }
        events.subscribe<WindowAction.ToggleToolWindow> { toggled += it.name }
        EditorInputHandler(mockk(relaxed = true), mockk(relaxed = true), EditorInputState(), engine, settingsManager, ViewportFocusReport()) { textInput }
    }

    @Test
    fun `alt 4 toggles the console`() {
        handler.update()
        assertEquals(listOf("window.console"), toggled)
    }

    @Test
    fun `alt f12 toggles the shell`() {
        key = GLFW.GLFW_KEY_F12
        handler.update()
        assertEquals(listOf("window.shell"), toggled)
    }

    @Test
    fun `the digit alone does nothing`() {
        alt = false
        handler.update()
        assertEquals(emptyList<String>(), toggled)
    }

    @Test
    fun `not while typing`() {
        textInput = true
        handler.update()
        assertEquals(emptyList<String>(), toggled)
    }
}
