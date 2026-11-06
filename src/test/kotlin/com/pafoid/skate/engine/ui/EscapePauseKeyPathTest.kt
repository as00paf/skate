package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.input.ActionInputReader
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.ProviderRawInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE
import java.io.File

/**
 * GUI-14: in the exported game (no ImGui context) Esc must reach the pause menu through the real key path:
 * GLFW key callback -> KeyListener -> ProviderRawInput -> ActionInputReader (`ui.cancel`) -> ReaderUiInputSource ->
 * UiInputSystem -> PauseMenuSystem. Before the fix, KeyListener asked ImGui for keyboard capture, which ends the
 * process when no ImGui context exists: the game closed on Esc.
 */
class EscapePauseKeyPathTest {
    private val provider = InputProvider(LoggerService())
    private val reader = ActionInputReader(ProviderRawInput(provider)).also { UiActions.bindDefaults(it.actions) }
    private var quits = 0
    private val manager = SystemManager()
    private val pause = PauseMenuSystem(setFrozen = { manager.paused = it }, actions = PauseMenuActions(quit = { quits++ }))
    private val input = UiInputSystem(ReaderUiInputSource(reader)).also { it.onUnhandledCancel = pause::open }
    private val scene = Scene("Level")

    init {
        listOf(pause, UiLayoutSystem(screen = { UiScreenSize(1920, 1080) }), input).forEach { manager.addSystem(it) }
        manager.start()
        manager.loadScene(scene)
        scene.isRunning = true
        frame()
    }

    /** One game frame, in Engine order: reader edges, systems, then the listeners' end of frame. */
    private fun frame() {
        reader.update()
        manager.update(1f / 60f)
        provider.keyListener.endFrame()
    }

    private fun tapEscape() {
        provider.keyListener.keyCallback(0L, GLFW_KEY_ESCAPE, 0, GLFW_PRESS, 0)
        frame()
        provider.keyListener.keyCallback(0L, GLFW_KEY_ESCAPE, 0, GLFW_RELEASE, 0)
        frame()
    }

    @Test
    fun GameEscape_OpensThePauseMenu_SecondEscapeResumes_NeverQuits() {
        tapEscape()
        assertTrue(pause.isOpen, "Esc opens the pause menu")
        assertTrue(manager.paused, "the game is frozen while paused")

        tapEscape()
        assertFalse(pause.isOpen, "Esc again resumes")
        assertFalse(manager.paused)

        repeat(3) { tapEscape() }
        assertTrue(pause.isOpen)
        assertEquals(0, quits, "only the Quit button closes the game")
    }

    @Test
    fun GameInput_KeysAndClicksWorkWithoutAnImGuiContext() {
        provider.keyListener.keyCallback(0L, GLFW_KEY_ESCAPE, 0, GLFW_PRESS, 0)
        assertTrue(provider.keyBeginPress(GLFW_KEY_ESCAPE))
        assertTrue(provider.isKeyPressed(GLFW_KEY_ESCAPE))
        provider.mouseListener.mouseButtonCallback(0L, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0)
        assertTrue(provider.isLeftMouseButtonDown())
    }

    @Test
    fun EditorCaptureHooks_StillHideInputFromTheGame() {
        provider.keyListener.captureKeyboard = { true }
        provider.mouseListener.captureMouse = { true }
        provider.keyListener.keyCallback(0L, GLFW_KEY_ESCAPE, 0, GLFW_PRESS, 0)
        provider.mouseListener.mouseButtonCallback(0L, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0)
        assertFalse(provider.keyBeginPress(GLFW_KEY_ESCAPE))
        assertFalse(provider.isLeftMouseButtonDown())
        assertTrue(provider.isLeftMouseButtonDown(ignoreImGui = true))
    }

    @Test
    fun Engine_DoesNotCallImGui() {
        val engineDir = File("src/main/kotlin/com/pafoid/skate/engine")
        val offenders = engineDir.walkTopDown().filter { it.extension == "kt" }
            .filter { f -> f.readLines().any { it.trim().startsWith("import imgui") } }
            .map { it.relativeTo(engineDir).invariantSeparatorsPath }.toList()
        assertEquals(emptyList<String>(), offenders, "engine code runs in the exported game, which has no ImGui context")
    }
}
