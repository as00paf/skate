package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.commands.project.CreateProjectCommand
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.loading.LoadingRunner
import com.pafoid.skate.editor.loading.StepExecutor
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.editor.ui.handlers.ProjectActionHandler
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * EDT-70: New Project's "Copy engine default assets" reaches the copier through the editor's create flow (event,
 * loading steps, command), in both modes, and is not stored in the project (EDT-59 Q2). That both kinds of project
 * open, play and export is `ProjectLifecycleEndToEndTest` (steps 1-8 copy, step 9 references).
 */
class EngineDefaultsChoiceTest {
    // Not @TempDir: copied assets can stay locked on Windows and fail JUnit's cleanup.
    private val dir: File = kotlin.io.path.createTempDirectory("skate-defaults-choice").toFile()

    private val serializer = Serializer()
    private val events = EventSystem()
    private val sceneManager = SceneManager(mockk(relaxed = true), events, serializer, mockk(relaxed = true), mockk(relaxed = true))
    private val engine = mockk<Engine>(relaxed = true).also { engine ->
        every { engine.eventSystem } returns events
        every { engine.sceneManager } returns sceneManager
        every { engine.serializer } returns serializer
        every { engine.jobSystem.isMainThread() } returns true
        every { engine.prefabsGenerator.createDefaultScenes(any()) } answers {
            val main = Scene("MainScene")
            val folder = firstArg<File>().also { it.mkdirs() }
            File(folder, "MainScene.scene").writeText(serializer.encodeFile(FileKind.SCENE, main))
            listOf(main)
        }
    }
    private val projects = ProjectManager(engine)
    private val settings = EditorSettingsManager(serializer, events, mockk(relaxed = true), mockk(relaxed = true), File(dir, "editor.settings"))

    init {
        val inline = object : StepExecutor {
            override fun background(block: () -> Unit) = block()
            override fun main(block: () -> Unit) = block()
        }
        ProjectActionHandler(engine, projects, settings, UndoRedoManager(events, mockk(relaxed = true)), mockk(relaxed = true), LoadingRunner(events, inline))
    }

    @AfterEach
    fun cleanup() {
        projects.closeProject()
        runCatching { dir.deleteRecursively() }
    }

    private fun create(name: String, copy: Boolean): File {
        events.publish(ProjectEvent.CreateProjectRequested(name, dir.absolutePath, copyEngineDefaults = copy))
        projects.closeProject()
        return File(dir, name)
    }

    private fun file(project: File, reference: String) = File(project, "Assets/" + EngineDefaults.logicalPath(reference))

    private fun projectJson(project: File): JsonObject =
        Json.parseToJsonElement(File(project, "${project.name}.skateproject").readText()).jsonObject

    @Test
    fun TheWizard_CopiesByDefault() {
        assertTrue(ProjectWizard().copyEngineDefaults)
        assertTrue(ProjectEvent.CreateProjectRequested("P", "x").copyEngineDefaults)
    }

    @Test
    fun Copy_PutsTheWholeCatalogInTheProject() {
        val project = create("Copied", copy = true)
        val missing = EngineDefaults.CATALOG.filterNot { file(project, it).isFile }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun Reference_CopiesOnlyTheAlwaysCopiedDefaults() {
        val project = create("Referenced", copy = false)
        assertFalse(file(project, EngineDefaults.Models.JAMES).exists(), "no models")
        assertFalse(file(project, EngineDefaults.Textures.METAL).exists(), "no textures")
        assertFalse(file(project, EngineDefaults.Sounds.MUSIC).exists(), "no sounds")
        val missing = EngineDefaults.ALWAYS_COPIED.filterNot { file(project, it).isFile }
        assertEquals(emptyList<String>(), missing, "basic shaders and skater configs are always copied")
    }

    @Test
    fun TheChoice_IsNotStoredInTheProject() {
        val copied = projectJson(create("A", copy = true))
        val referenced = projectJson(create("B", copy = false))
        assertEquals(copied.keys, referenced.keys, "both modes save the same project fields")
        val suspicious = copied.keys.filter { it.contains("copy", ignoreCase = true) || it.contains("engineDefault", ignoreCase = true) }
        assertEquals(emptyList<String>(), suspicious)
    }

    @Test
    fun TheCommand_HonoursTheChoice_WhenItPreparesTheFolderItself() {
        // A failed background step leaves no folder; the command then prepares it (EDT-56 retry).
        val command = CreateProjectCommand(ProjectEvent.CreateProjectRequested("Retry", dir.absolutePath, copyEngineDefaults = false), projects)
        command.execute()
        assertTrue(command.wasSuccessful(), command.getFailureReason())
        assertFalse(file(File(dir, "Retry"), EngineDefaults.Models.JAMES).exists())
    }
}
