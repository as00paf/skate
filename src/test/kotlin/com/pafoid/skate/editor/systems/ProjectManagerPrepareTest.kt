package com.pafoid.skate.editor.systems

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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-56: the background halves of create and open leave the open project alone. */
class ProjectManagerPrepareTest {
    // Not @TempDir: copied bundled assets can stay locked on Windows and fail JUnit's cleanup.
    private val dir: File = kotlin.io.path.createTempDirectory("skate-prepare").toFile()
    private val serializer = Serializer()
    private val sceneManager = SceneManager(mockk(relaxed = true), EventSystem(), serializer, mockk(relaxed = true), mockk(relaxed = true))
    private val engine = mockk<Engine>(relaxed = true).also { engine ->
        every { engine.eventSystem } returns EventSystem()
        every { engine.sceneManager } returns sceneManager
        every { engine.serializer } returns serializer
        every { engine.prefabsGenerator.createDefaultScenes(any()) } answers {
            val main = Scene("MainScene").also { it.addChild(GameObject("Player")) }
            val folder = firstArg<File>().also { it.mkdirs() }
            File(folder, "MainScene.scene").writeText(serializer.encodeFile(FileKind.SCENE, main))
            listOf(main)
        }
    }
    private val projects = ProjectManager(engine)

    @AfterEach
    fun cleanup() {
        runCatching { dir.deleteRecursively() }
    }

    private fun projectFile(name: String) = File(dir, "$name/$name.skateproject")

    @Test
    fun PrepareProject_DecodesScenesWithoutTouchingTheOpenProject() {
        assertTrue(projects.createProject("B", dir).isSuccess)
        assertTrue(projects.createProject("A", dir).isSuccess)
        val rootOfA = serializer.assetPaths.projectRoot

        val prepared = projects.prepareProject(projectFile("B"))

        assertNotNull(prepared)
        assertEquals("B", prepared!!.project.name)
        assertEquals(listOf("MainScene"), prepared.scenes.scenes.map { it.name })
        assertEquals(listOf("Player"), prepared.scenes.scenes.single().children.map { it.name })
        assertTrue(prepared.scenes.failed.isEmpty())
        assertEquals("A", projects.currentProject?.name)
        assertEquals(rootOfA, serializer.assetPaths.projectRoot)
    }

    @Test
    fun OpenProjectFile_UsesThePreparedScenes() {
        assertTrue(projects.createProject("B", dir).isSuccess)
        val prepared = projects.prepareProject(projectFile("B"))!!
        projects.closeProject()
        // Proof the scene is not read again: the file on disk now holds a different scene.
        File(dir, "B/Scenes/MainScene.scene").writeText(serializer.encodeFile(FileKind.SCENE, Scene("MainScene")))

        assertTrue(projects.openProjectFile(projectFile("B"), prepared))

        assertEquals("B", projects.currentProject?.name)
        assertEquals(listOf("Player"), sceneManager.currentScene?.children?.map { it.name })
    }

    @Test
    fun PrepareProject_MissingFile_FailsWithAReason() {
        assertNull(projects.prepareProject(File(dir, "nope.skateproject")))
        assertTrue(projects.lastError!!.startsWith("File not found"))
        assertFalse(projects.openProjectFile(File(dir, "nope.skateproject")))
    }

    @Test
    fun PrepareProjectFolder_WritesTheFoldersAndRefusesAnExistingProject() {
        val folder = projects.prepareProjectFolder("C", dir).getOrThrow()
        assertTrue(File(folder, "Scenes").isDirectory)
        assertTrue(File(folder, "Assets/strings.properties").isFile)
        assertNull(projects.currentProject, "the folder step opens nothing")

        assertTrue(projects.createProject("C", dir, preparedDir = folder).isSuccess)
        assertTrue(projects.prepareProjectFolder("C", dir).isFailure)
    }
}
