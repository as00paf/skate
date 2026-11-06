package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.imgui.GraphicsBackendField
import com.pafoid.skate.editor.project.ProjectFileReader
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.editor.ui.handlers.ProjectActionHandler
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/** EDT-43: the graphics backend is chosen in New Project and Project Settings, undoably, and saved with the project. */
class GraphicsBackendSettingTest {
    // Not @TempDir: copied bundled assets can stay locked on Windows and fail JUnit's cleanup.
    private val dir: File = kotlin.io.path.createTempDirectory("skate-backend").toFile()

    private val serializer = Serializer()
    private val events = EventSystem()
    private val sceneManager = SceneManager(mockk(relaxed = true), events, serializer, mockk(relaxed = true), mockk(relaxed = true))
    private val engine = mockk<Engine>(relaxed = true).also { engine ->
        every { engine.eventSystem } returns events
        every { engine.sceneManager } returns sceneManager
        every { engine.serializer } returns serializer
        every { engine.jobSystem.isMainThread() } returns true // handlers run their main-thread work inline
        every { engine.prefabsGenerator.createDefaultScenes(any()) } answers {
            val main = Scene("MainScene")
            val folder = firstArg<File>().also { it.mkdirs() }
            File(folder, "MainScene.scene").writeText(serializer.encodeFile(FileKind.SCENE, main))
            listOf(main)
        }
    }
    private val projects = ProjectManager(engine)
    private val settings = EditorSettingsManager(serializer, events, mockk(relaxed = true), mockk(relaxed = true), File(dir, "editor.settings"))
    private val undo = UndoRedoManager(events, mockk(relaxed = true))

    init {
        ProjectActionHandler(engine, projects, settings, undo, mockk(relaxed = true))
    }

    @AfterEach
    fun cleanup() {
        runCatching { dir.deleteRecursively() }
    }

    private fun create(backend: GraphicsBackend? = null): File {
        val event = if (backend == null) ProjectEvent.CreateProjectRequested("P", dir.absolutePath)
        else ProjectEvent.CreateProjectRequested("P", dir.absolutePath, backend)
        events.publish(event)
        return File(dir, "P/P.skateproject")
    }

    private fun savedBackend(file: File) = ProjectFileReader.read(serializer, file).graphicsBackend

    @Test
    fun NewProject_DefaultsToOpenGl() {
        assertEquals(GraphicsBackend.OPENGL, ProjectWizard().graphicsBackend)
        assertEquals(GraphicsBackend.OPENGL, savedBackend(create()))
    }

    @Test
    fun NewProject_WithVulkan_IsSavedWithIt() {
        val file = create(GraphicsBackend.VULKAN)
        assertEquals(GraphicsBackend.VULKAN, projects.currentProject?.graphicsBackend)
        assertEquals(GraphicsBackend.VULKAN, savedBackend(file))
    }

    @Test
    fun ProjectSettings_ChangeIsUndoable_AndSaved() {
        val file = create()
        events.publish(ProjectEvent.SetGraphicsBackendRequested(GraphicsBackend.VULKAN))
        assertEquals(GraphicsBackend.VULKAN, projects.currentProject?.graphicsBackend)

        undo.undo()
        assertEquals(GraphicsBackend.OPENGL, projects.currentProject?.graphicsBackend)
        undo.redo()
        assertEquals(GraphicsBackend.VULKAN, projects.currentProject?.graphicsBackend)

        events.publish(ProjectEvent.SaveRequested) // OK / Apply in Project Settings
        projects.closeProject()
        assertTrue(projects.openProjectFile(file))
        assertEquals(GraphicsBackend.VULKAN, projects.currentProject?.graphicsBackend)
    }

    @Test
    fun PickingTheSameBackend_AddsNothingToTheHistory() {
        create()
        val before = undo.getUndoCount()
        events.publish(ProjectEvent.SetGraphicsBackendRequested(GraphicsBackend.OPENGL))
        assertEquals(before, undo.getUndoCount())
    }

    @Test
    fun Strings_ExistInBothLanguages_AndSayTheEditorStaysOnOpenGl() {
        val keys = listOf(GraphicsBackendField.LABEL_KEY, GraphicsBackendField.TOOLTIP_KEY, GraphicsBackendField.WARNING_KEY,
            "settings.project.graphics", "settings.project.graphics.description") + GraphicsBackend.entries.map { it.labelKey }
        val files = File("assets/strings/editor").listFiles { f -> f.extension == "properties" }!!.toList()
        files.partition { it.name.endsWith("_fr.properties") }.toList().forEach { group ->
            val props = Properties().apply { group.forEach { f -> f.reader(Charsets.UTF_8).use { load(it) } } }
            assertEquals(emptyList<String>(), keys.filter { props.getProperty(it).isNullOrBlank() }, group.first().name)
            assertTrue(props.getProperty(GraphicsBackendField.TOOLTIP_KEY).contains("OpenGL"))
            assertTrue(props.getProperty(GraphicsBackend.VULKAN.labelKey).contains("exp"), "Vulkan is labelled experimental")
        }
    }
}
