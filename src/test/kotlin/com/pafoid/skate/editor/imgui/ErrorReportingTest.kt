package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.commands.project.LoadLastProjectCommand
import com.pafoid.skate.editor.commands.project.OpenProjectFileCommand
import com.pafoid.skate.editor.commands.project.SaveSceneCommand
import com.pafoid.skate.editor.events.EditorError
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-10: failed user actions reach the error dialog, not only the console. */
class ErrorReportingTest {
    private val dir: File = kotlin.io.path.createTempDirectory("skate-errors").toFile()
    private val events = EventSystem()
    private val shown = mutableListOf<EditorError>().also { list -> events.subscribe<EditorError> { list += it } }
    private val serializer = Serializer()
    private val sceneManager = SceneManager(mockk(relaxed = true), events, serializer, mockk(relaxed = true), mockk(relaxed = true))
    private val engine = mockk<Engine>(relaxed = true).also {
        every { it.eventSystem } returns events
        every { it.sceneManager } returns sceneManager
        every { it.serializer } returns serializer
    }
    private val projects = ProjectManager(engine)
    private val undo = UndoRedoManager(events, mockk(relaxed = true))

    @AfterEach
    fun cleanup() {
        runCatching { dir.deleteRecursively() }
    }

    @Test
    fun Queue_ShowsErrorsInOrder_AndCollapsesRepeats() {
        val queue = ErrorQueue(capacity = 2)
        val a = EditorError("error.save_scene.title", "a")
        queue.push(a)
        queue.push(a)
        queue.push(EditorError("error.save_project.title"))
        queue.push(EditorError("error.open_project.title"))
        assertEquals(2, queue.size)
        assertEquals(a, queue.current)
        queue.dismiss()
        assertEquals("error.save_project.title", queue.current?.titleKey)
    }

    @Test
    fun MessageKey_DefaultsToTheTitlesSibling() {
        assertEquals("error.save_scene.message", EditorError("error.save_scene.title").messageKey)
    }

    @Test
    fun OpenCorruptProject_ShowsTheError_AndDoesNotThrow() {
        val file = File(dir, "Broken.skateproject").also { it.writeText("{ not json") }
        undo.executeCommand(OpenProjectFileCommand(projects, file))

        assertFalse(projects.hasProject())
        assertEquals("error.open_project.title", shown.single().titleKey)
        assertTrue(shown.single().detail!!.contains("Broken.skateproject"), shown.single().detail)
    }

    @Test
    fun OpenMissingProject_ShowsTheError() {
        undo.executeCommand(OpenProjectFileCommand(projects, File(dir, "Gone.skateproject")))
        assertTrue(shown.single().detail!!.startsWith("File not found"), shown.single().detail)
    }

    @Test
    fun SaveSceneWithoutProject_ShowsTheError() {
        undo.executeCommand(SaveSceneCommand(Scene("Main"), projects, sceneManager))
        assertEquals("error.save_scene.title", shown.single().titleKey)
    }

    @Test
    fun NoRecentProject_IsNotAnError() {
        val settings = EditorSettingsManager(serializer, events, mockk(relaxed = true), mockk(relaxed = true), File(dir, "editor.settings"))
        undo.executeCommand(LoadLastProjectCommand(projects, settings))
        assertTrue(shown.isEmpty())
        assertNull(projects.lastError)
    }
}
