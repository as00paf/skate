package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
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

    @Test
    fun ProjectManager_CreateProject_OpensMainSceneExactlyOnce() {
        val serializer = Serializer()
        val sceneManager = spyk(SceneManager(mockk(relaxed = true), EventSystem(), serializer, mockk(relaxed = true), mockk(relaxed = true)))
        val engine = mockk<Engine>(relaxed = true)
        every { engine.eventSystem } returns EventSystem()
        every { engine.sceneManager } returns sceneManager
        every { engine.serializer } returns serializer
        every { engine.prefabsGenerator.createDefaultScenes(any()) } answers {
            val main = Scene("MainScene")
            val folder = firstArg<File>().also { it.mkdirs() }
            File(folder, "MainScene.scene").writeText(
                serializer.encodeFile(com.pafoid.skate.engine.assets.serialization.FileKind.SCENE, main)
            )
            listOf(main)
        }

        assertTrue(ProjectManager(engine).createProject("P", dir).isSuccess)

        verify(exactly = 1) { sceneManager.openScenes(any()) }
        assertEquals(listOf("MainScene"), sceneManager.openScenes.map { it.name })
    }

    @Test
    fun ProjectManager_CreateProject_ContainsAndOpensTheFeatureShowcase() {
        val serializer = Serializer()
        val sceneManager = SceneManager(mockk(relaxed = true), EventSystem(), serializer, mockk(relaxed = true), mockk(relaxed = true))
        val assets = mockk<com.pafoid.skate.engine.assets.AssetsManager>(relaxed = true)
        val skeleton = com.pafoid.skate.engine.assets.data.models.animations.Skeleton(
            com.pafoid.skate.engine.assets.data.models.animations.Bone(0, "root"), 1
        )
        every { assets.loadModel(any()) } answers { com.pafoid.skate.engine.assets.data.models.`3dModel`(firstArg(), skeleton = skeleton) }
        every { assets.loadAnimationSync(any()) } answers {
            com.pafoid.skate.engine.assets.data.models.animations.Animation("clip", emptyList(), 1f, firstArg())
        }
        every { assets.getTexture(any()) } answers { com.pafoid.skate.engine.assets.data.Texture(filePath = firstArg()) }
        val engine = mockk<Engine>(relaxed = true)
        every { engine.eventSystem } returns EventSystem()
        every { engine.sceneManager } returns sceneManager
        every { engine.serializer } returns serializer
        every { engine.assetsManager } returns assets
        val prefabs = com.pafoid.skate.engine.assets.PrefabsGenerator(engine)
        every { engine.prefabsGenerator } returns prefabs

        val result = ProjectManager(engine).createProject("P", dir)

        assertTrue(result.isSuccess, result.exceptionOrNull()?.toString())
        val project = result.getOrThrow()
        assertTrue(project.scenesPath.any { it.endsWith("FeatureShowcase.scene") }, project.scenesPath.toString())
        assertTrue(File(dir, "P/Scenes/FeatureShowcase.scene").exists())
        assertEquals(listOf("MainScene", "FeatureShowcase"), sceneManager.openScenes.map { it.name })
        assertEquals("MainScene", sceneManager.currentScene?.name)
        val showcase = sceneManager.openScenes.single { it.name == "FeatureShowcase" }
        assertTrue(showcase.children.any { it.name == "[Vehicle] Car" })
        // AUD-10: the audio emitter's MP3 resolves to the project's copy of the bundled track.
        val music = showcase.children.single { it.name == "[Audio] Emitter3D" }.components
            .filterIsInstance<com.pafoid.skate.engine.ecs.components.AudioComponent>().single()
        assertTrue(File(music.soundFilePath).isFile, music.soundFilePath)
        assertTrue(music.soundFilePath.startsWith(File(dir, "P/Assets").absolutePath))
    }
}
