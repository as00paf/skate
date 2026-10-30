package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import java.io.File

class ProjectManagerCreateTest {
    // Not @TempDir: copied bundled assets can stay locked on Windows and fail JUnit's cleanup.
    private val dir: File = kotlin.io.path.createTempDirectory("skate-create").toFile()

    @AfterEach
    fun cleanup() {
        runCatching { dir.deleteRecursively() }
    }

    @Test
    fun ProjectManager_CreateProject_OpensDefaultSceneSoHierarchyShowsIt() {
        val serializer = Serializer()
        val sceneManager = SceneManager(mockk(relaxed = true), EventSystem(), serializer, mockk(relaxed = true), mockk(relaxed = true))
        val engine = mockk<Engine>(relaxed = true)
        every { engine.eventSystem } returns EventSystem()
        every { engine.sceneManager } returns sceneManager
        every { engine.serializer } returns serializer
        every { engine.prefabsGenerator.createDefaultScenes(any()) } answers {
            val main = Scene("MainScene").also { it.addChild(GameObject("Player")) }
            val folder = firstArg<File>().also { it.mkdirs() }
            File(folder, "MainScene.scene").writeText(
                serializer.encodeFile(com.pafoid.skate.engine.assets.serialization.FileKind.SCENE, main)
            )
            listOf(main)
        }

        val result = ProjectManager(engine).createProject("P", dir)

        assertTrue(result.isSuccess, result.exceptionOrNull()?.toString())
        assertEquals("MainScene", sceneManager.currentScene?.name)
        assertEquals(listOf("Player"), sceneManager.currentScene?.children?.map { it.name })
    }
}
