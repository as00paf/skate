package com.pafoid.skate.templates

import com.pafoid.skate.editor.project.ProjectFileReader
import com.pafoid.skate.editor.vcs.JGitVcsService
import com.pafoid.skate.editor.vcs.VcsAuthor
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectCreatorTest {

    private val dir: File = Files.createTempDirectory("skate-creator").toFile()
    private val prefabs = mockk<PrefabsGenerator>(relaxed = true).also {
        every { it.spawnDefaultsSync() } answers { listOf(GameObject("Skater"), GameObject("Skateboard")) }
    }
    private val author = VcsAuthor("Test", "test@example.com")

    private fun creator() = ProjectCreator(Serializer(), author = { author })

    private fun create(name: String = "Game", settings: NewProjectSettings = NewProjectSettings(EmptyTemplate.ID, copyEngineDefaults = false)) =
        creator().create(name, dir, settings, prefabs).getOrThrow()

    private fun read(projectDir: File, name: String) =
        ProjectFileReader.read(Serializer(AssetPaths().apply { projectRoot = projectDir }), File(projectDir, "$name.skateproject"))

    private fun png(name: String, bytes: ByteArray = byteArrayOf(1, 2, 3)) = File(dir, name).also { it.writeBytes(bytes) }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun ProjectCreator_EmptyTemplateWritesAProjectTheReaderDecodes() {
        val project = create()
        val projectDir = File(dir, "Game")
        val read = read(projectDir, "Game")
        assertEquals("Game", read.name)
        assertEquals(listOf("MainScene.scene"), read.scenesPath.map { File(it).name })
        assertEquals(EngineDefaults.Textures.APP_ICON, read.iconPath)
        assertTrue(File(projectDir, "Scenes/MainScene.scene").isFile)
        assertTrue(File(project.projectPath).isAbsolute, "the returned project keeps absolute paths")
        val saved = File(projectDir, "Game.skateproject").readText()
        assertFalse(saved.contains(projectDir.absolutePath.replace("\\", "\\\\")), "saved project-relative")
        assertTrue(File(projectDir, "Assets/strings.properties").readText().startsWith("project.name=Game"))
    }

    @Test
    fun ProjectCreator_SettingsBecomeProjectFields() {
        val splash = png("logo.PNG")
        create(settings = NewProjectSettings(
            EmptyTemplate.ID, targetFps = 60, physicsBackend = PhysicsBackend.JOLT, graphicsBackend = GraphicsBackend.VULKAN,
            splashImage = splash, copyEngineDefaults = false,
        ))
        val projectDir = File(dir, "Game")
        val project = read(projectDir, "Game")
        assertEquals(60, project.targetFps)
        assertEquals(PhysicsBackend.JOLT, project.physicsBackend)
        assertEquals(GraphicsBackend.VULKAN, project.graphicsBackend)
        val copied = File(ProjectLayout.assetsDir(projectDir), "Textures/splash.png")
        assertTrue(copied.isFile)
        assertEquals(copied.canonicalFile, File(project.splashPath).canonicalFile)
    }

    @Test
    fun ProjectCreator_DefaultSettingsAreUncappedBulletAndNoSplash() {
        create()
        val project = read(File(dir, "Game"), "Game")
        assertEquals(0, project.targetFps)
        assertEquals(PhysicsBackend.BULLET, project.physicsBackend)
        assertEquals("", project.splashPath)
    }

    @Test
    fun ProjectCreator_SplashClashGetsANumberedName() {
        val projectDir = File(dir, "Game")
        File(projectDir, "Assets/Textures").mkdirs()
        File(projectDir, "Assets/Textures/splash.png").writeBytes(byteArrayOf(9))
        create(settings = NewProjectSettings(EmptyTemplate.ID, splashImage = png("s.png"), copyEngineDefaults = false))
        assertEquals("splash_2.png", File(read(projectDir, "Game").splashPath).name)
        assertEquals(9, File(projectDir, "Assets/Textures/splash.png").readBytes().single().toInt(), "the user's file is kept")
    }

    @Test
    fun ProjectCreator_SkateGameHasOnlyTheSkateScene() {
        create("Skate", NewProjectSettings(SkateGameTemplate.ID, copyEngineDefaults = false))
        val projectDir = File(dir, "Skate")
        assertEquals(listOf("MainScene.scene"), File(projectDir, "Scenes").list()!!.sorted())
        val scene = Serializer(AssetPaths().apply { projectRoot = projectDir })
            .decodeFile<Scene>(FileKind.SCENE, File(projectDir, "Scenes/MainScene.scene").readText())
        assertTrue(scene.children.any { it.name == "Skater" }, scene.children.map { it.name }.toString())
    }

    @Test
    fun ProjectCreator_RefusesAnExistingProjectButAcceptsANonEmptyFolder() {
        File(dir, "Game").mkdirs()
        File(dir, "Game/notes.txt").writeText("mine")
        create()
        assertEquals("mine", File(dir, "Game/notes.txt").readText())
        val again = creator().create("Game", dir, NewProjectSettings(EmptyTemplate.ID, copyEngineDefaults = false), prefabs)
        assertTrue(again.isFailure)
        assertTrue(again.exceptionOrNull()!!.message!!.contains("already exists"))
    }

    @Test
    fun ProjectCreator_ReportsInvalidSettings() {
        val problems = creator().problems("Bad:Name", File(dir, "missing"), NewProjectSettings("nope", targetFps = 5, splashImage = png("a.gif")))
        assertEquals(5, problems.size, problems.toString())
    }

    @Test
    fun ProjectCreator_CopyModeCopiesTheEngineDefaultsAndReferenceModeDoesNot() {
        create("Copy", NewProjectSettings(EmptyTemplate.ID, copyEngineDefaults = true))
        create("Ref", NewProjectSettings(EmptyTemplate.ID, copyEngineDefaults = false))
        assertTrue(File(dir, "Copy/Assets/Models/cube.obj").isFile)
        assertFalse(File(dir, "Ref/Assets/Models/cube.obj").exists())
        assertTrue(File(dir, "Ref/Assets/Shaders/shader_3d_default.glsl").isFile, "basic shaders in both modes")
    }

    @Test
    fun ProjectCreator_AiFilesOnlyWhenSelectedAndExistingFilesKept() {
        create("Plain")
        assertFalse(File(dir, "Plain/AGENTS.md").exists())

        File(dir, "Ai/.github").mkdirs()
        File(dir, "Ai/.github/copilot-instructions.md").writeText("mine")
        create("Ai", NewProjectSettings(TetrisTemplate.ID, includeAiFiles = true, copyEngineDefaults = false))
        val agents = File(dir, "Ai/AGENTS.md").readText()
        assertTrue(agents.contains("# Ai:") && agents.contains("`tetris` template"), agents.lines().take(3).toString())
        assertFalse(agents.contains("{{"))
        assertTrue(File(dir, "Ai/.claude/skills/skate-project/SKILL.md").readText().replace("\r\n", "\n").startsWith("---\nname: skate-project"))
        assertEquals("mine", File(dir, "Ai/.github/copilot-instructions.md").readText())
    }

    @Test
    fun ProjectCreator_VersionControlInitialisesAndCommitsTheProject() {
        create("Plain")
        assertFalse(File(dir, "Plain/.git").exists())
        assertFalse(File(dir, "Plain/.gitignore").exists())

        create("Repo", NewProjectSettings(EmptyTemplate.ID, versionControl = true, copyEngineDefaults = false))
        val repo = File(dir, "Repo")
        assertTrue(File(repo, ".gitignore").readText().contains("Builds/"))
        assertTrue(File(repo, ".gitattributes").isFile)
        val vcs = JGitVcsService(repo)
        assertTrue(vcs.isRepository())
        assertEquals(1, vcs.log(10).size)
        assertTrue(vcs.status().isEmpty(), "everything is in the first commit: ${vcs.status()}")
    }

    @Test
    fun NewProjectSettings_ValidatesFpsAndSplash() {
        assertTrue(NewProjectSettings.isValidTargetFps(0))
        assertTrue(NewProjectSettings.isValidTargetFps(15) && NewProjectSettings.isValidTargetFps(240))
        assertFalse(NewProjectSettings.isValidTargetFps(14) || NewProjectSettings.isValidTargetFps(241))
        assertTrue(NewProjectSettings.isValidSplash(null))
        assertTrue(NewProjectSettings.isValidSplash(png("a.jpg")))
        assertFalse(NewProjectSettings.isValidSplash(png("a.bmp")))
        assertFalse(NewProjectSettings.isValidSplash(File(dir, "missing.png")))
    }
}
