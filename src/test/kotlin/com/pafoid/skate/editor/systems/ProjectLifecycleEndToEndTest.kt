package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.editor.commands.project.LoadLastProjectCommand
import com.pafoid.skate.editor.commands.project.OpenProjectFileCommand
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.ExportWizard
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.ui.handlers.ProjectActionHandler
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.audio.FakeAudioBackend
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.JobSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.ecs.systems.AudioSystem
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.ParticleSystem
import com.pafoid.skate.engine.ecs.systems.PhysicsFrameSystem
import com.pafoid.skate.engine.ecs.systems.PhysicsSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.ecs.systems.TransformSystem
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.bullet.BulletPhysics3D
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.ExportTarget
import com.pafoid.skate.engine.utils.ProjectExporter
import com.pafoid.skate.game.GameBin
import com.pafoid.skate.game.loadProjectAndIcon
import com.pafoid.skate.game.readFileHeader
import com.pafoid.skate.game.samples.FeatureShowcase
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import com.pafoid.skate.engine.core.Window
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.vulkan.FrameDiff
import com.pafoid.skate.engine.render.rhi.vulkan.RhiBackend
import com.pafoid.skate.engine.render.rhi.vulkan.VulkanDevice
import com.pafoid.skate.engine.ui.GameSettings
import com.pafoid.skate.engine.ui.GameSettingsStore
import com.pafoid.skate.game.GameGraphics
import com.pafoid.skate.testing.GpuTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwInit
import java.nio.ByteBuffer
import java.io.File

/**
 * QA-14: the editor project lifecycle end to end, the way the human goes through it, on real files:
 * create a new project, change and save it, close it, reopen it from File > Recent Projects and from the startup
 * "last project" path (a fresh settings manager reading the user-data file, like a restarted editor), move the
 * folder and open it there, switch scenes, Play/Stop, export through the export wizard and open the exported
 * `game.bin` headless with GameMain's own readers.
 *
 * Real: [ProjectManager], [EditorSettingsManager] (settings file in a temp user-data dir), `ProjectFileReader`,
 * [SceneManager] and [Serializer]/`AssetPaths`, [ProjectActionHandler] + [ExportWizard] + [ProjectExporter], the
 * Play-time systems (Bullet physics, animation, particles, audio on [FakeAudioBackend]) and GameMain's pack readers.
 * Faked: only what needs a GPU: [AssetsManager]'s model/texture/clip/sound decoding. Those stubs first resolve the
 * requested path the way the real loader does (`paths.localPath`, or the pack in the exported game) and record any
 * path that does not exist, so a broken reference fails here instead of in the human's editor.
 *
 * Steps share one editor session and run in order; every step also fails on any ERROR log. Known bugs are stepped
 * over only through [KNOWN_ISSUES] (remove the entry when fixed).
 *
 * Steps 9-10 (QA-16) repeat create/save/reopen/export for a project whose exported game runs on Vulkan, then start
 * that export the way GameMain does. Step 10 needs a GPU and `SKATE_RHI=vulkan` ([GpuTest], docs/testing/QA-16.md).
 *
 * **Extend this test when a workflow changes** (project/scene file format, paths, settings, export, game startup):
 * add the human's new step here, through the same managers, instead of a narrower test that skips the disk.
 * Regressions it would have caught: EDT-41 (recent/startup reopen after AST-09 made projectPath relative; verified by
 * reverting the fix) and AUD-09 (silent showcase audio in Play).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ProjectLifecycleEndToEndTest {

    // Not @TempDir: copied bundled assets can stay locked on Windows and fail JUnit's cleanup.
    private val dir: File = kotlin.io.path.createTempDirectory("skate-lifecycle").toFile()
    private val userData = File(dir, "UserData").also { it.mkdirs() }
    private val settingsFile = File(userData, "editor.settings")
    private val workspace = File(dir, "Workspace").also { it.mkdirs() }

    private val logger = LoggerService().also { it.consoleSink = { _, _ -> } }
    private val serializer = Serializer()
    private val events = EventSystem(logger)
    private val jobs = JobSystem(logger = logger)
    private val systems = SystemManager()
    private val unresolved = mutableListOf<String>()
    private val assets = gpuFreeAssets(serializer, logger, unresolved)
    private val sceneManager = SceneManager(assets, events, serializer, systems, logger)
    private val al = FakeAudioBackend()
    private val debug = mockk<DebugRenderer>(relaxed = true)
    private lateinit var world: BulletPhysics3D

    private val engine = mockk<Engine>(relaxed = true).also {
        every { it.assetsManager } returns assets
        every { it.sceneManager } returns sceneManager
        every { it.serializer } returns serializer
        every { it.eventSystem } returns events
        every { it.logger } returns logger
        every { it.systemManager } returns systems
        every { it.jobSystem } returns jobs
    }
    private val projects = ProjectManager(engine)
    private val settings = EditorSettingsManager(serializer, events, logger, mockk(relaxed = true), settingsFile)
    private val wizard = ExportWizard()

    private val projectName = "Lifecycle"
    private lateinit var projectFile: File
    private lateinit var exportDir: File
    private lateinit var vkProjectFile: File
    private lateinit var vkExportDir: File
    private lateinit var gameSkater: GameObject
    private var gameErrors: List<String> = emptyList()

    @BeforeAll
    fun startEditor() {
        NativeLibraryLoader().loadNativeLibrary()
        world = BulletPhysics3D(debug)
        every { engine.prefabsGenerator } returns PrefabsGenerator(engine)
        val physics = PhysicsSystem(debug, events, physics3d = world)
        val cameras = CameraManager(events)
        listOf(
            GameObjectManager(), TransformSystem(), cameras,
            AudioSystem(AudioEngine(logger, al.backend), logger, assets, cameras),
            physics, PhysicsFrameSystem(physics), AnimationSystem(), ParticleSystem(),
        ).forEach { systems.addSystem(it) }
        systems.start()
        settings.loadSettings() // first launch: creates the settings file
        ProjectActionHandler(engine, projects, settings, mockk(relaxed = true), mockk(relaxed = true))
        events.subscribe<ProjectEvent.ExportProgressed> { wizard.onProgress(it.progress) }
        events.subscribe<ProjectEvent.ExportFinished> { wizard.onFinished(it.result) }
        logger.clearLogs()
    }

    @AfterAll
    fun cleanup() {
        runCatching { systems.destroy() }
        runCatching { world.destroy() }
        runCatching { jobs.destroy() }
        runCatching { dir.deleteRecursively() }
    }

    private fun assertNoErrors(step: String) {
        val errors = errorsIn(logger)
        assertEquals(emptyList<String>(), errors, "ERROR logs during '$step'")
        assertEquals(emptyList<String>(), unresolved.filterNot(::isKnownIssue), "unresolved asset references during '$step'")
        logger.clearLogs()
        unresolved.clear()
    }

    /** The settings file as a restarted editor reads it (its own manager, nothing shared with this session). */
    private fun restartedSettings() =
        EditorSettingsManager(Serializer(), EventSystem(), logger, mockk(relaxed = true), settingsFile).also { it.loadSettings() }

    private fun scene(name: String) = sceneManager.openScenes.single { it.name == name }

    private fun assertProjectOpen(file: File, step: String) {
        val project = projects.currentProject
        assertNotNull(project, "no project open after $step")
        assertEquals(file.absolutePath, project!!.projectPath, step)
        assertEquals(listOf("MainScene", FeatureShowcase.SCENE_NAME), sceneManager.openScenes.map { it.name }, step)
        assertEquals("MainScene", sceneManager.currentScene?.name, step)
        assertTrue(File(project.defaultScene).isFile, "$step: default scene ${project.defaultScene}")
        project.scenesPath.forEach { assertTrue(File(it).isFile, "$step: scene $it") }
        // Edits saved in step 1 survive every reopen.
        assertEquals(EDITED_PHYSICS_FPS, project.physicsFPS, "$step: project settings")
        assertEquals(EDITED_DESCRIPTION, project.description, "$step: project settings")
        assertNotNull(scene("MainScene").children.singleOrNull { it.name == ADDED_OBJECT }, "$step: saved scene edit")
        assertReferencesResolve(step)
    }

    /** Controllers and `.skmat` materials, which fail silently on load, plus sounds on disk. */
    private fun assertReferencesResolve(step: String) {
        val all = sceneManager.openScenes.flatMap { s -> s.children.flatMap { listOf(it) + it.children } }
        val controlled = all.filter { it.getComponent<Animator>()?.controllerPath != null }
        assertTrue(controlled.isNotEmpty(), "$step: no animator with a controller")
        controlled.forEach { assertNotNull(it.getComponent<Animator>()!!.stateMachine, "$step: ${it.name} controller not loaded") }
        val materials = all.mapNotNull { it.getComponent<RenderComponent>() }.filter { it.materialPath != null }
        assertTrue(materials.isNotEmpty(), "$step: no .skmat material in the default project")
        materials.forEach {
            assertNull(it.resolveMaterialAsset(assets::readText) { p -> Texture(filePath = p) }, "$step: ${it.materialPath}")
        }
        val sounds = all.mapNotNull { it.getComponent<AudioComponent>()?.soundFilePath }
        assertTrue(sounds.isNotEmpty(), "$step: no sound in the default project")
        sounds.forEach { assertTrue(File(assets.paths.localPath(it)).isFile, "$step: sound $it") }
    }

    @Test
    @Order(1)
    fun Lifecycle_1_CreateEditAndSaveANewProject() {
        val created = projects.createProject(projectName, workspace)
        assertTrue(created.isSuccess, created.exceptionOrNull()?.toString())
        projectFile = File(workspace, "$projectName/$projectName.skateproject").absoluteFile
        assertTrue(projectFile.isFile)

        val project = projects.currentProject!!
        project.physicsFPS = EDITED_PHYSICS_FPS
        project.description = EDITED_DESCRIPTION
        val main = scene("MainScene")
        main.addChild(GameObject(ADDED_OBJECT).also { it.addComponent(Transform(Vector3f(1f, 2f, 3f))) })
        assertTrue(sceneManager.saveScene(main, File(project.defaultScene).parent))
        assertTrue(projects.saveProject())

        assertEquals(projectFile.absolutePath, settings.recentProjects.first().projectPath)
        assertProjectOpen(projectFile, "create")
        assertNoErrors("create")
    }

    @Test
    @Order(2)
    fun Lifecycle_2_CloseThenReopenFromRecentProjects() {
        projects.closeProject()
        assertFalse(projects.hasProject())
        assertTrue(sceneManager.openScenes.isEmpty())

        // File > Recent Projects in a restarted editor: the list is read from the user-data settings file.
        val recent = restartedSettings().recentProjects
        assertEquals(projectFile.absolutePath, recent.first().projectPath)
        val open = OpenProjectFileCommand(projects, File(recent.first().projectPath))
        open.execute()
        assertTrue(open.wasSuccessful(), "Recent Projects could not open ${recent.first().projectPath}")
        assertProjectOpen(projectFile, "reopen from Recent Projects")
        assertNoErrors("reopen from Recent Projects")
    }

    @Test
    @Order(3)
    fun Lifecycle_3_CloseThenReopenAtStartupAsLastProject() {
        projects.closeProject()
        // What EditorScreen runs at startup, with the settings a restarted editor reads from disk.
        val load = LoadLastProjectCommand(projects, restartedSettings())
        load.execute()
        assertTrue(load.wasSuccessful(), "startup could not reopen the last project")
        assertProjectOpen(projectFile, "startup last project")
        assertNoErrors("startup last project")
    }

    @Test
    @Order(4)
    fun Lifecycle_4_MoveTheProjectFolderAndOpenIt() {
        projects.closeProject()
        val moved = File(dir, "Moved").also { it.mkdirs() }
        File(workspace, projectName).copyRecursively(File(moved, projectName))
        File(workspace, projectName).deleteRecursively()
        assertFalse(projectFile.exists())
        assertTrue(restartedSettings().recentProjects.isEmpty(), "the old location is gone")

        projectFile = File(moved, "$projectName/$projectName.skateproject").absoluteFile
        assertTrue(projects.openProjectFile(projectFile))
        assertProjectOpen(projectFile, "open moved project")
        val root = projectFile.parentFile.absolutePath
        assertTrue(projects.currentProject!!.defaultScene.startsWith(root), projects.currentProject!!.defaultScene)
        assertTrue(assets.paths.localPath(scene(FeatureShowcase.SCENE_NAME).children
            .first { it.getComponent<AudioComponent>() != null }.getComponent<AudioComponent>()!!.soundFilePath).startsWith(root))
        assertEquals(projectFile.absolutePath, restartedSettings().recentProjects.first().projectPath)
        assertNoErrors("open moved project")
    }

    @Test
    @Order(5)
    fun Lifecycle_5_SwitchToTheShowcasePlayThenStop() {
        val showcase = scene(FeatureShowcase.SCENE_NAME)
        sceneManager.switchScene(showcase)
        assertEquals(FeatureShowcase.SCENE_NAME, sceneManager.currentScene?.name)

        // Play: snapshot like ViewportActionHandler, then run frames like Engine.updateRunningState.
        val snapshot = ScenePlaySnapshot.capture(showcase, serializer)
        val box = showcase.children.single { it.name == "[Physics] Box_4" }
        val startY = box.getComponent<Transform>()!!.translation.y
        val character = showcase.children.single { it.name == "[Animation] Character" }.getComponent<Animator>()!!
        repeat(120) {
            systems.update(DT)
            showcase.isRunning = true
            showcase.update(DT)
        }
        assertNotNull(box.getComponent<RigidBody3D>()!!.rawBody, "physics body not created in Play")
        assertTrue(box.getComponent<Transform>()!!.translation.y < startY - 0.05f, "physics did not run in Play")
        assertTrue(character.currentTime > 1.9f, "animation did not run in Play: ${character.currentTime}")
        assertTrue(al.playing.isNotEmpty(), "no audio source playing in Play (AUD-09)")

        // Stop: the scene goes back to its edit-time state and the sound stops.
        val restored = snapshot.restore(sceneManager, serializer, showcase)
        restored.isRunning = false
        systems.update(DT)
        assertEquals(restored, sceneManager.currentScene)
        assertEquals(startY, restored.children.single { it.name == "[Physics] Box_4" }.getComponent<Transform>()!!.translation.y, 1e-4f)
        assertTrue(al.playing.isEmpty(), "audio still playing after Stop: ${al.playing.size}")
        sceneManager.switchScene(scene("MainScene"))
        assertNoErrors("Play/Stop")
    }

    @Test
    @Order(6)
    fun Lifecycle_6_ExportThroughTheWizard() {
        exportDir = exportWithWizard(File(dir, "Export"))
        assertNoErrors("export")
    }

    /** File > Export: the wizard, Pack only, into [output]; waits for the background export like the editor's frames. */
    private fun exportWithWizard(output: File): File {
        val project = projects.currentProject!!
        wizard.load(project, ProjectExporter(logger).sceneFiles(project, wizard.options(project)))
        assertEquals(2, wizard.includedSceneCount)
        wizard.target = ExportTarget.PACK_ONLY
        wizard.outputDir = output.path
        assertTrue(wizard.canStart(project, gameJar = null), "wizard issues: ${wizard.issues(project, null)}")
        wizard.start()
        events.publish(ProjectEvent.ExportRequested(wizard.options(project)))

        val deadline = System.currentTimeMillis() + 60_000
        while (wizard.phase != ExportWizard.Phase.SUCCEEDED && wizard.phase != ExportWizard.Phase.FAILED) {
            assertTrue(System.currentTimeMillis() < deadline, "export never finished (phase ${wizard.phase})")
            jobs.update()
            Thread.sleep(10)
        }
        assertEquals(ExportWizard.Phase.SUCCEEDED, wizard.phase, "export failed: ${wizard.result}")
        assertTrue(File(output, "game.bin").isFile)
        wizard.reset()
        return output
    }

    @Test
    @Order(7)
    fun Lifecycle_7_ExportedGameOpensHeadlessFromThePackAlone() {
        projects.closeProject()
        // The game runs from the export folder only: hide the project so nothing can fall back to it.
        val projectDir = projectFile.parentFile
        val hidden = File(dir, "Hidden")
        assertTrue(projectDir.renameTo(hidden), "could not move the project away")

        // GPL-18: GameMain's own lookup, as when the exported jar is double-clicked or started from another folder.
        val launchedFrom = File(dir, "Elsewhere").also { it.mkdirs() }
        val exportedJar = File(exportDir, "skate-game.jar").also { it.writeText("stand-in: the PACK_ONLY export has no jar") }
        val bin = (GameBin.read(LoggerService(), GameBin.candidates(null, exportedJar, launchedFrom)) ?: fail("game.bin not found next to the jar")).bytes
        val (atlas, headerSize) = readFileHeader(bin)
        assertNotNull(atlas, "game.bin header unreadable")
        val (packed, _) = loadProjectAndIcon(bin, atlas!!, headerSize)
        assertNotNull(packed, "no project file in game.bin")
        assertExportedSettings(packed!!)

        // A fresh process: its own serializer, asset manager and scene manager, like GameMain's Engine.
        val gameLogger = LoggerService().also { it.consoleSink = { _, _ -> } }
        val gameSerializer = Serializer()
        val gameUnresolved = mutableListOf<String>()
        val gameAssets = gpuFreeAssets(gameSerializer, gameLogger, gameUnresolved)
        val gameEvents = EventSystem(gameLogger)
        val gameScenes = SceneManager(gameAssets, gameEvents, gameSerializer, SystemManager(), gameLogger)
        val game = mockk<Engine>(relaxed = true).also {
            every { it.assetsManager } returns gameAssets
            every { it.sceneManager } returns gameScenes
            every { it.serializer } returns gameSerializer
            every { it.eventSystem } returns gameEvents
            every { it.logger } returns gameLogger
        }
        assertTrue(ProjectManager(game).openProject(packed, bin, atlas, headerSize), "the exported game could not open its project")
        val main = gameScenes.currentScene
        assertEquals("MainScene", main?.name)
        assertNotNull(main!!.children.singleOrNull { it.name == ADDED_OBJECT }, "saved scene edit missing from the export")
        gameSkater = main.children.flatMap { listOf(it) + it.children }.first { it.getComponent<Animator>()?.controllerPath != null }

        // The other exported scene, with the .skmat material and the sound, resolves from the pack too.
        val showcasePath = packed.scenesPath.single { it.endsWith("${FeatureShowcase.SCENE_NAME}.scene") }
        val showcase = gameSerializer.decodeFile<Scene>(FileKind.SCENE, gameAssets.readText(showcasePath)!!)
        gameScenes.resolveSceneReferences(showcase)
        val all = showcase.children.flatMap { listOf(it) + it.children }
        all.mapNotNull { it.getComponent<RenderComponent>() }.filter { it.materialPath != null }.forEach {
            assertNull(it.resolveMaterialAsset(gameAssets::readText) { p -> Texture(filePath = p) }, "packed ${it.materialPath}")
        }
        all.mapNotNull { it.getComponent<AudioComponent>()?.soundFilePath }.forEach {
            assertTrue(File(gameAssets.paths.localPath(it)).isFile, "packed sound $it")
        }
        gameErrors = errorsIn(gameLogger)
        assertEquals(emptyList<String>(), gameUnresolved.filterNot(::isKnownIssue), "references missing from game.bin")
        hidden.renameTo(projectDir)
    }

    /**
     * BUG-021 (ANI): fails until fixed. `ProjectManager.openProject` loads the clips from the pack, then
     * `SceneManager.openScene` runs `Animator.resolveAnimationsFromPaths`, which checks `File(path).exists()` on the
     * pack-relative paths and drops every clip, so the controller is rejected and the exported skater does not animate.
     */
    @Test
    @Order(8)
    fun Lifecycle_8_ExportedSkaterKeepsItsClipsAndControllerWithoutErrors() {
        val animator = gameSkater.getComponent<Animator>()!!
        assertTrue(animator.animations.isNotEmpty(), "${gameSkater.name} lost its clips in the exported game")
        assertNotNull(animator.stateMachine, "${gameSkater.name} controller not loaded from the pack")
        assertEquals(emptyList<String>(), gameErrors, "ERROR logs in the exported game")
    }

    @Test
    @Order(9)
    fun Lifecycle_9_VulkanProjectSavesReopensAndExportsWithItsBackend() {
        // New Project wizard with "Graphics backend: Vulkan" (EDT-43), saved, closed and reopened from Recent Projects.
        // AST-27: this one references the engine defaults instead of copying them (only the basic shaders are copied).
        val created = projects.createProject(VK_PROJECT, workspace, GraphicsBackend.VULKAN, copyDefaults = false)
        assertTrue(created.isSuccess, created.exceptionOrNull()?.toString())
        vkProjectFile = File(workspace, "$VK_PROJECT/$VK_PROJECT.skateproject").absoluteFile
        val vkAssets = File(vkProjectFile.parentFile, "Assets")
        assertFalse(File(vkAssets, "Models/james.glb").exists(), "reference mode copies no models")
        assertTrue(File(vkAssets, "Shaders/shader_3d_default.glsl").isFile, "basic shaders are always copied")
        assertTrue(projects.saveProject())
        projects.closeProject()
        val recent = restartedSettings().recentProjects.first()
        assertEquals(vkProjectFile.absolutePath, recent.projectPath)
        assertTrue(projects.openProjectFile(File(recent.projectPath)))
        assertEquals(GraphicsBackend.VULKAN, projects.currentProject!!.graphicsBackend, "backend lost on reopen")
        assertReferencesResolve("open Vulkan project")

        vkExportDir = exportWithWizard(File(dir, "ExportVulkan"))
        val bin = File(vkExportDir, "game.bin").readBytes()
        val (atlas, headerSize) = readFileHeader(bin)
        val (packed, _) = loadProjectAndIcon(bin, atlas!!, headerSize)
        assertEquals(GraphicsBackend.VULKAN, packed!!.graphicsBackend, "packed project")
        // Every engine default the scenes reference ships in the pack under its `engine:` reference (AST-26 §4.6).
        val entries = atlas.values.flatten().associateBy { it.path }
        fun packedText(path: String) = entries.getValue(path).let { PackBuilder.extract(bin, it.position + headerSize, it.size, it.compressed) }.toString(Charsets.UTF_8)
        val sceneRefs = packed.scenesPath.flatMap { path -> Regex("\"(engine:[^\"]+)\"").findAll(packedText(path)).map { it.groupValues[1] }.toList() }.toSet()
        val packedPaths = entries.keys
        assertTrue(EngineDefaults.Models.JAMES in sceneRefs, "scenes reference engine defaults: $sceneRefs")
        assertEquals(emptyList<String>(), (sceneRefs - packedPaths).sorted(), "engine defaults missing from the reference-mode pack")
        val store = GameSettingsStore(GameSettings.fileIn(vkExportDir), logger)
        assertEquals(GraphicsBackend.VULKAN, store.settings.graphicsBackend, "game.settings.json")
        // GameMain's choice: no -D override, the player's settings file, then the packed project.
        assertEquals(GraphicsBackend.VULKAN, GameGraphics.requested(packed.graphicsBackend, store, property = null))
        projects.closeProject()
        assertNoErrors("Vulkan project export")
    }

    /**
     * The Vulkan export started like GameMain: [GameGraphics.start] opens a (hidden until shown) Vulkan [Window], the
     * engine starts on its device, opens the pack and plays MainScene with every pass, presenting each frame. Without a
     * Vulkan driver the game must fall back to OpenGL with the documented log line, and still run.
     */
    @Test
    @Order(10)
    @GpuTest
    fun Lifecycle_10_VulkanExportStartsOnVulkanOrFallsBackWithTheDocumentedLog() {
        assumeTrue(RhiBackend.selected() == RhiBackend.VULKAN, "Vulkan is opt-in: set SKATE_RHI=vulkan")
        assumeTrue(glfwInit(), "no GLFW")
        val bin = File(vkExportDir, "game.bin").readBytes()
        val (atlas, headerSize) = readFileHeader(bin)
        val (packed, _) = loadProjectAndIcon(bin, atlas!!, headerSize)
        val store = GameSettingsStore(GameSettings.fileIn(vkExportDir), logger)

        val engine = Engine()
        engine.logger.consoleSink = { _, _ -> }
        val started = GameGraphics.start(GameGraphics.requested(packed!!.graphicsBackend, store, property = null), engine.logger, store) { backend ->
            Window(1280, 720, packed.name, backend = backend)
        }
        val window = started.window
        val startLog = engine.logger.logs.map { it.message }
        if (started.fellBack) {
            assertTrue(window.device !is VulkanDevice)
            assertTrue(startLog.any { it.startsWith("Graphics backend VULKAN could not start") && it.endsWith("falling back to OPENGL.") }, "fallback log: $startLog")
            assertTrue(store.settings.graphicsNote?.contains("The game ran on OPENGL") == true, "fallback note in game.settings.json")
        } else {
            assertEquals(GraphicsBackend.VULKAN, started.backend)
            assertTrue(window.device is VulkanDevice, "the game runs on ${window.device::class.simpleName}")
            assertTrue(startLog.any { it.startsWith("Graphics backend: VULKAN") }, "start log: $startLog")
        }
        println("QA-16: exported game requested ${started.requested}, runs on ${started.backend} (${window.device::class.simpleName})")
        engine.logger.clearLogs()
        try {
            engine.start(window.glfwWindow, window.device)
            assertTrue(ProjectManager(engine).openProject(packed, bin, atlas, headerSize), "the exported game could not open its project")
            assertEquals("MainScene", engine.sceneManager.currentScene?.name)
            engine.runtimePlaying = true
            val disabled = engine.renderer.renderGraph.getAllPasses().filter { !it.isEnabled }.map { it.name }
            repeat(120) {
                engine.update(1f / 60f)
                window.device.commandList().blit(engine.renderer.frameBuffer.target, null, dstRect = IntRect(0, 0, window.width, window.height), filter = Filter.LINEAR)
                window.device.endFrame()
            }
            val pixels = ByteBuffer.allocateDirect(GAME_W * GAME_H * 4)
            window.device.readPixels(engine.renderer.frameBuffer.target, 0, IntRect(0, 0, GAME_W, GAME_H), PixelFormat.RGBA8, pixels)
            val frame = ByteArray(pixels.capacity()).also { pixels.get(it) }
            assertTrue(FrameDiff.distinctColours(frame) > 20, "the game frame is flat (${FrameDiff.distinctColours(frame)} colours)")
            val skater = engine.sceneManager.currentScene!!.children.flatMap { listOf(it) + it.children }
                .first { it.getComponent<Animator>()?.controllerPath != null }.getComponent<Animator>()!!
            assertNotNull(skater.stateMachine, "the exported skater's controller did not load")
            assertEquals(emptyList<String>(), errorsIn(engine.logger), "ERROR logs while the game ran (disabled passes: $disabled)")
        } finally {
            runCatching { engine.destroy() }
            runCatching { window.device.close() }
            glfwDestroyWindow(window.glfwWindow)
        }
    }

    private fun assertExportedSettings(packed: Project) {
        assertEquals(EDITED_PHYSICS_FPS, packed.physicsFPS)
        assertEquals(EDITED_DESCRIPTION, packed.description)
        assertFalse(File(packed.defaultScene).isAbsolute, "machine path in the exported project: ${packed.defaultScene}")
    }

    private companion object {
        const val VK_PROJECT = "LifecycleVulkan"
        const val GAME_W = 640
        const val GAME_H = 360
        /**
         * Known bugs this test steps over so that it keeps guarding the rest of the lifecycle. Remove an entry when its
         * bug is fixed. Matched against `/`-normalized error messages and missed paths.
         */
        val KNOWN_ISSUES = mapOf<String, String>()

        fun isKnownIssue(text: String) = text.replace('\\', '/').let { t -> KNOWN_ISSUES.keys.any { it in t } }

        fun errorsIn(logger: LoggerService) = logger.logs.filter { it.level == LogLevel.ERROR }
            .map { "${it.source}: ${it.message}" }.filterNot(::isKnownIssue)

        const val DT = 1f / 60f
        const val EDITED_PHYSICS_FPS = 120
        const val EDITED_DESCRIPTION = "edited in QA-14"
        const val ADDED_OBJECT = "Added by QA-14"

        /**
         * The real [AssetsManager] (paths, pack resolver, text reads) with only GPU/OpenAL decoding stubbed. Each stub
         * resolves the path like the real loader (editor: `paths.localPath` on disk; game: the pack) and records misses.
         */
        /** Models get a humanoid rig (controllers fit clips to the hips); clips are named like the real loader names clips, so controllers find their states. */
        fun clip(path: String) = Animation(AnimationLoader.clipName(path), emptyList(), 1f, path)

        fun gpuFreeAssets(serializer: Serializer, logger: LoggerService, misses: MutableList<String>): AssetsManager {
            val assets = spyk(AssetsManager(serializer, logger))
            fun onDisk(path: String) { if (!File(assets.paths.localPath(path)).isFile) misses += path }
            fun inPack(path: String) { if (assets.assetsResolver.find(path) == null) misses += path }
            every { assets.loadModel(any<String>()) } answers {
                onDisk(firstArg()); `3dModel`(firstArg(), skeleton = TestHumanoid.skeleton())
            }
            every { assets.getTexture(any()) } answers { onDisk(firstArg()); Texture(filePath = firstArg()) }
            every { assets.loadAnimationSync(any()) } answers { onDisk(firstArg()); clip(firstArg()) }
            every { assets.getSound(any()) } answers { onDisk(firstArg()); SoundBuffer(firstArg()).apply { bufferId = 1 } }
            every { assets.resolveModel(any()) } answers {
                inPack(firstArg()); `3dModel`(firstArg(), skeleton = TestHumanoid.skeleton())
            }
            every { assets.resolveTexture(any()) } answers { inPack(firstArg()); Texture(filePath = firstArg()) }
            every { assets.resolveAnimation(any()) } answers { inPack(firstArg()); clip(firstArg()) }
            return assets
        }
    }
}
