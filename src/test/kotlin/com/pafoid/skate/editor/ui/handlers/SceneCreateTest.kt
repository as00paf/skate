package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.loading.LoadingRunner
import com.pafoid.skate.editor.loading.StepExecutor
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.SceneAction
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-99: one New Scene request (+ button, File menu, shortcut) creates exactly one scene, and undo removes it. */
class SceneCreateTest {
    @TempDir
    lateinit var projectDir: File

    private val events = EventSystem()
    private val serializer = Serializer()
    private val sceneManager = SceneManager(mockk(relaxed = true), events, serializer, mockk(relaxed = true), mockk(relaxed = true))
    private val engine = mockk<Engine>(relaxed = true).also {
        every { it.eventSystem } returns events
        every { it.sceneManager } returns sceneManager
        every { it.serializer } returns serializer
        every { it.runtimePlaying } returns false
        every { it.logger } returns LoggerService()
    }
    private val undo = UndoRedoManager(events, LoggerService())
    private val main = Scene("Main")

    private fun setUp() {
        val projectManager = mockk<ProjectManager>(relaxed = true).also {
            every { it.getProjectDirectory() } returns projectDir
        }
        val editorCamera = mockk<EditorCamera>(relaxed = true).also {
            every { it.camera } returns CameraComponent()
            every { it.transform } returns Transform()
        }
        val inline = object : StepExecutor {
            override fun background(block: () -> Unit) = block()
            override fun main(block: () -> Unit) = block()
        }
        SceneActionHandler(engine, projectManager, undo, editorCamera, LoadingRunner(events, inline))
        sceneManager.openScene(main)
    }

    private fun scenesDir() = ProjectLayout.scenesDir(projectDir)

    private fun entries() = scenesDir().listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun SceneCreate_OneRequest_OpensOneSceneAndWritesOneFile() {
        setUp()

        events.publish(SceneAction.CreateRequested)

        assertEquals(listOf("Main", "NewScene_1"), sceneManager.openScenes.map { it.name })
        assertEquals("NewScene_1", sceneManager.currentScene?.name)
        assertEquals(listOf("NewScene_1.scene"), entries())
        assertTrue(File(scenesDir(), "NewScene_1.scene").isFile, "the scene is a file, not a folder")
    }

    @Test
    fun SceneCreate_TwoRequests_NumberTheScenes() {
        setUp()

        events.publish(SceneAction.CreateRequested)
        events.publish(SceneAction.CreateRequested)

        assertEquals(listOf("Main", "NewScene_1", "NewScene_2"), sceneManager.openScenes.map { it.name })
        assertEquals(listOf("NewScene_1.scene", "NewScene_2.scene"), entries())
    }

    @Test
    fun SceneCreate_Undo_ClosesSceneDeletesFileAndReturnsToPrevious() {
        setUp()
        events.publish(SceneAction.CreateRequested)

        undo.undo()

        assertEquals(listOf("Main"), sceneManager.openScenes.map { it.name })
        assertSame(main, sceneManager.currentScene)
        assertEquals(emptyList<String>(), entries())
    }

    @Test
    fun SceneCreate_Redo_CreatesItAgainOnce() {
        setUp()
        events.publish(SceneAction.CreateRequested)
        undo.undo()

        undo.redo()

        assertEquals(listOf("Main", "NewScene_1"), sceneManager.openScenes.map { it.name })
        assertEquals(listOf("NewScene_1.scene"), entries())
    }

    @Test
    fun SceneCreate_PlusButton_IsATabButtonNotATab() {
        // A "+" tab stays selected for a frame after the click, so beginTabItem fired twice per click
        val source = File("src/main/kotlin/com/pafoid/skate/editor/imgui/EditorScenesTabBar.kt").readText()

        assertTrue(Regex("""tabItemButton\(\s*Icons\.PLUS""").containsMatchIn(source))
        assertFalse(Regex("""beginTabItem\(\s*Icons\.PLUS""").containsMatchIn(source))
    }
}
