package com.pafoid.skate.editor.systems

import com.pafoid.skate.templates.NewProjectSettings
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
        every { engine.prefabsGenerator.spawnDefaultsSync() } answers { listOf(GameObject("Player")) }

        val result = ProjectManager(engine).createProject("P", dir)

        assertTrue(result.isSuccess, result.exceptionOrNull()?.toString())
        assertEquals("MainScene", sceneManager.currentScene?.name)
        assertEquals(listOf("Sun", "Player"), sceneManager.currentScene?.children?.map { it.name })
    }

    @Test
    fun ProjectManager_CreateProject_EveryTemplateCreatesAndReopens() {
        val serializer = Serializer()
        val engine = mockk<Engine>(relaxed = true)
        every { engine.eventSystem } returns EventSystem()
        every { engine.serializer } returns serializer
        val assets = mockk<com.pafoid.skate.engine.assets.AssetsManager>(relaxed = true)
        val skeleton = com.pafoid.skate.engine.assets.data.models.animations.Skeleton(
            com.pafoid.skate.engine.assets.data.models.animations.Bone(0, "root"), 1
        )
        every { assets.loadModel(any()) } answers { com.pafoid.skate.engine.assets.data.models.`3dModel`(firstArg(), skeleton = skeleton) }
        every { assets.loadAnimationSync(any()) } answers {
            com.pafoid.skate.engine.assets.data.models.animations.Animation("clip", emptyList(), 1f, firstArg())
        }
        every { assets.getTexture(any()) } answers { com.pafoid.skate.engine.assets.data.Texture(filePath = firstArg()) }
        every { engine.assetsManager } returns assets
        every { engine.prefabsGenerator } returns com.pafoid.skate.engine.assets.PrefabsGenerator(engine)
        com.pafoid.skate.templates.TemplateRegistry.all().forEach { template ->
            val sceneManager = SceneManager(mockk(relaxed = true), EventSystem(), serializer, mockk(relaxed = true), mockk(relaxed = true))
            every { engine.sceneManager } returns sceneManager
            val projects = ProjectManager(engine)
            val name = "T_" + template.id.replace('-', '_')

            val created = projects.createProject(name, dir, NewProjectSettings(template = template.id))

            assertTrue(created.isSuccess, "${template.id}: ${created.exceptionOrNull()}")
            assertEquals(template.sceneNames.toSet(), sceneManager.openScenes.map { it.name }.toSet(), template.id)
            projects.closeProject()
            assertTrue(projects.openProjectFile(File(dir, "$name/$name.skateproject")), template.id)
        }
    }

    @Test
    fun ProjectManager_CreateProject_OpensMainSceneExactlyOnce() {
        val serializer = Serializer()
        val sceneManager = spyk(SceneManager(mockk(relaxed = true), EventSystem(), serializer, mockk(relaxed = true), mockk(relaxed = true)))
        val engine = mockk<Engine>(relaxed = true)
        every { engine.eventSystem } returns EventSystem()
        every { engine.sceneManager } returns sceneManager
        every { engine.serializer } returns serializer

        assertTrue(ProjectManager(engine).createProject("P", dir).isSuccess)

        verify(exactly = 1) { sceneManager.openScenes(any()) }
        assertEquals(listOf("MainScene"), sceneManager.openScenes.map { it.name })
    }

    @Test
    fun ProjectManager_CreateProject_FeatureShowcaseTemplate_ContainsAndOpensTheFeatureShowcase() {
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

        val result = ProjectManager(engine).createProject("P", dir, NewProjectSettings(template = "feature-showcase"))

        assertTrue(result.isSuccess, result.exceptionOrNull()?.toString())
        val project = result.getOrThrow()
        assertTrue(project.scenesPath.any { it.endsWith("FeatureShowcase.scene") }, project.scenesPath.toString())
        assertTrue(File(dir, "P/Scenes/FeatureShowcase.scene").exists())
        assertEquals(listOf("FeatureShowcase"), sceneManager.openScenes.map { it.name })
        assertEquals("FeatureShowcase", sceneManager.currentScene?.name)
        assertTrue(!File(dir, "P/Scenes/MainScene.scene").exists())
        val showcase = sceneManager.openScenes.single { it.name == "FeatureShowcase" }
        assertTrue(showcase.children.any { it.name == "[Vehicle] Car" })
        // AUD-10: the audio emitter's MP3 resolves to the project's copy of the bundled track.
        val music = showcase.children.single { it.name == "[Audio] Emitter3D" }.components
            .filterIsInstance<com.pafoid.skate.engine.ecs.components.AudioComponent>().single()
        assertTrue(File(music.soundFilePath).isFile, music.soundFilePath)
        assertTrue(music.soundFilePath.startsWith(File(dir, "P/Assets").absolutePath))
    }

    @Test
    fun ProjectManager_CreateProject_AppliesTheTemplateAndSettings() {
        val serializer = Serializer()
        val sceneManager = SceneManager(mockk(relaxed = true), EventSystem(), serializer, mockk(relaxed = true), mockk(relaxed = true))
        val engine = mockk<Engine>(relaxed = true)
        every { engine.eventSystem } returns EventSystem()
        every { engine.sceneManager } returns sceneManager
        every { engine.serializer } returns serializer
        val splash = File(dir, "my.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val settings = NewProjectSettings(
            template = "empty", targetFps = 90, physicsBackend = com.pafoid.skate.engine.physics3d.PhysicsBackend.SIMPLE,
            splashImage = splash, includeAiFiles = true, copyEngineDefaults = false,
        )

        val project = ProjectManager(engine).createProject("T", dir, settings).getOrThrow()

        assertEquals(90, project.targetFps)
        assertEquals(com.pafoid.skate.engine.physics3d.PhysicsBackend.SIMPLE, project.physicsBackend)
        assertTrue(project.splashPath.endsWith("splash.png"), project.splashPath)
        assertTrue(File(dir, "T/Assets/Textures/splash.png").isFile)
        assertTrue(File(dir, "T/AGENTS.md").isFile)
        assertEquals(listOf("MainScene"), sceneManager.openScenes.map { it.name })
        assertTrue(File(dir, "T/Assets/Models").isDirectory)
    }
}
