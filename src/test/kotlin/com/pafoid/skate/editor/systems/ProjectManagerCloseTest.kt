package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-45: closing a project announces it before its scenes are saved, so Play can stop and restore first. */
class ProjectManagerCloseTest {
    private val dir: File = kotlin.io.path.createTempDirectory("skate-close").toFile()
    private val serializer = Serializer()
    private val events = EventSystem()
    private val sceneManager = SceneManager(mockk(relaxed = true), events, serializer, mockk(relaxed = true), mockk(relaxed = true))
    private val engine = mockk<Engine>(relaxed = true).also {
        every { it.eventSystem } returns events
        every { it.sceneManager } returns sceneManager
        every { it.serializer } returns serializer
        every { it.prefabsGenerator.createDefaultScenes(any()) } answers {
            val main = Scene("MainScene").also { s -> s.addChild(GameObject("Player")) }
            val folder = firstArg<File>().also { f -> f.mkdirs() }
            File(folder, "MainScene.scene").writeText(serializer.encodeFile(FileKind.SCENE, main))
            listOf(main)
        }
    }

    @AfterEach
    fun cleanup() {
        runCatching { dir.deleteRecursively() }
    }

    @Test
    fun ProjectClose_PublishesClosingWhileScenesAreStillOpen() {
        val projects = ProjectManager(engine)
        projects.createProject("P", dir).getOrThrow()
        val seen = mutableListOf<String>()
        events.subscribe(ProjectEvent.Closing::class) { seen += "closing:${sceneManager.openScenes.size}" }
        events.subscribe(ProjectEvent.Closed::class) { seen += "closed:${sceneManager.openScenes.size}" }

        projects.closeProject()

        assertEquals(listOf("closing:1", "closed:0"), seen)
    }

    @Test
    fun ProjectClose_SavesTheSceneRestoredByStopNotThePlayState() {
        val projects = ProjectManager(engine)
        projects.createProject("P", dir).getOrThrow()
        val scene = sceneManager.currentScene!!
        val snapshot = ScenePlaySnapshot.capture(scene, serializer)
        // Play: runtime state that must never reach the scene file.
        scene.addChild(GameObject("SpawnedInPlay"))
        scene.isDirty = true
        events.subscribe(ProjectEvent.Closing::class) { snapshot.restore(sceneManager, serializer, scene) }

        projects.closeProject()

        val saved = File(dir, "P/Scenes").walkTopDown().filter { it.name == "MainScene.scene" }.toList()
        val names = saved.map { serializer.decodeFile<Scene>(FileKind.SCENE, it.readText()).children.map { c -> c.name } }
        names.forEach { assertEquals(listOf("Player"), it) }
    }
}
