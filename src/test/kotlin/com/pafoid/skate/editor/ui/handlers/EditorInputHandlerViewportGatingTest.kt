package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.data.ViewportFocusReport
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

/** EDT-73: the editor camera reads input only while the Game Viewport is focused (hovered for the wheel). */
class EditorInputHandlerViewportGatingTest {
    private val mappings = EditorInputMappings()
    private val input = mockk<InputProvider>(relaxed = true)
    private val state = EditorInputState()
    private val focus = ViewportFocusReport()
    private var textInput = false

    private val handler: EditorInputHandler = run {
        val engine = mockk<Engine>()
        val sceneManager = mockk<SceneManager>()
        val settingsManager = mockk<EditorSettingsManager>()
        every { engine.logger } returns mockk(relaxed = true)
        every { engine.eventSystem } returns EventSystem()
        every { engine.inputProvider } returns input
        every { engine.sceneManager } returns sceneManager
        every { sceneManager.currentScene } returns null
        every { settingsManager.editorSettings } returns EditorSettings(editorInputMappings = mappings)
        every { input.isControlKeyDown() } returns false
        every { input.keyBeginPress(any()) } returns false
        every { input.isKeyPressed(any()) } answers { firstArg<Int>() == mappings.moveForward.keyboardKey }
        every { input.isInsideViewport() } returns true
        every { input.isMiddleMouseButtonDown(true) } returns true
        every { input.middleMouseButtonBeginPress() } returns true
        every { input.getMouseDx() } returns 4f
        every { input.getMouseDy() } returns 2f
        every { input.getMouseScrollY() } returns 1f
        EditorInputHandler(mockk(relaxed = true), mockk(relaxed = true), state, engine, settingsManager, focus) { textInput }
    }

    @Test
    fun `focused viewport moves, looks, orbits and zooms`() {
        focus.report(focused = true, hovered = true)
        handler.update()
        assertEquals(1f, state.moveDirection.y)
        assertEquals(4f, state.mouseLook.x)
        assertTrue(state.orbitPressed)
        assertEquals(1f, state.mouseScroll)
    }

    @Test
    fun `keys typed in another window never move the camera`() {
        focus.report(focused = false, hovered = false)
        handler.update()
        assertEquals(0f, state.moveDirection.lengthSquared())
        assertEquals(0f, state.verticalMovement)
        assertEquals(0f, state.mouseLook.lengthSquared())
        assertFalse(state.orbitPressed)
        assertFalse(state.orbitHeld)
        assertEquals(0f, state.mouseScroll)
    }

    @Test
    fun `wheel zooms over an unfocused viewport but keys still do not fly`() {
        focus.report(focused = false, hovered = true)
        handler.update()
        assertEquals(1f, state.mouseScroll)
        assertEquals(0f, state.moveDirection.lengthSquared())
    }

    @Test
    fun `typing into a text field inside the viewport does not fly`() {
        textInput = true
        focus.report(focused = true, hovered = true)
        handler.update()
        assertEquals(0f, state.moveDirection.lengthSquared())
    }

    @Test
    fun `without a fresh report the viewport counts as unfocused`() {
        focus.report(focused = true, hovered = true)
        handler.update()
        handler.update()
        assertEquals(0f, state.moveDirection.lengthSquared())
        assertEquals(0f, state.mouseScroll)
    }
}
