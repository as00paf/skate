package com.pafoid.skate.editor.responsiveness

import com.pafoid.skate.editor.commands.project.OpenSceneFileCommand
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.ScenePlaySnapshot
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.loaders.TextureLoader
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.scripting.host.KotlinScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptLoadResult
import com.pafoid.skate.engine.scripting.host.ScriptSource
import com.pafoid.skate.game.samples.FeatureShowcase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import java.util.Collections

/**
 * QA-19: timings of the editor operations the human reported as sluggish (create project, Play) and the other heavy
 * main-thread paths, measured on the real code with only GPU work faked. Results go to stdout and to
 * `build/qa19-timings.txt`; docs/design/QA-19.md records them.
 *
 * The budgets are guards against regressions on paths that must stay on the main thread (Play capture and Stop
 * restore). They are about 10 times the measured value and take the best of several runs, so a slow CI machine
 * does not fail them. The work the audit moves off the main thread (decodes, script compiles) is only measured here.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class EditorResponsivenessTimingTest {

    // Not @TempDir: copied bundled assets can stay locked on Windows and fail JUnit's cleanup.
    private val dir: File = kotlin.io.path.createTempDirectory("qa19-timing").toFile()
    private val serializer = Serializer()
    private val events = EventSystem()
    private val systems = SystemManager()
    private val textures: MutableSet<String> = Collections.synchronizedSet(linkedSetOf())
    private val models: MutableSet<String> = Collections.synchronizedSet(linkedSetOf())
    private val assets = mockk<AssetsManager>(relaxed = true).also {
        every { it.loadModel(any<String>()) } answers { models += firstArg<String>(); `3dModel`(firstArg(), skeleton = Skeleton(Bone(0, "root"), 1)) }
        every { it.loadAnimationSync(any()) } answers { Animation("clip", emptyList(), 1f, firstArg()) }
        every { it.getTexture(any()) } answers { textures += firstArg<String>(); Texture(filePath = firstArg()) }
        every { it.getSound(any()) } answers { SoundBuffer(firstArg()).apply { bufferId = 1 } }
    }
    private val sceneManager = SceneManager(assets, events, serializer, systems, LoggerService())
    private lateinit var projects: ProjectManager
    private lateinit var prefabs: PrefabsGenerator
    private val report = StringBuilder()

    @BeforeAll
    fun setUp() {
        NativeLibraryLoader().loadNativeLibrary()
        val engine = mockk<Engine>(relaxed = true)
        every { engine.assetsManager } returns assets
        every { engine.sceneManager } returns sceneManager
        every { engine.serializer } returns serializer
        every { engine.eventSystem } returns events
        every { engine.logger } returns LoggerService()
        every { engine.systemManager } returns systems
        prefabs = PrefabsGenerator(engine)
        every { engine.prefabsGenerator } returns prefabs
        projects = ProjectManager(engine)
    }

    @AfterAll
    fun tearDown() {
        println(report)
        runCatching { File("build/qa19-timings.txt").apply { parentFile.mkdirs() }.writeText(report.toString()) }
        runCatching { systems.destroy() }
        runCatching { dir.deleteRecursively() }
    }

    private fun record(name: String, ms: Double) {
        report.appendLine("QA-19 timing | %-48s | %9.1f ms".format(name, ms))
    }

    private inline fun <T> timed(block: () -> T): Pair<T, Double> {
        val start = System.nanoTime()
        val value = block()
        return value to (System.nanoTime() - start) / 1e6
    }

    /** Create Project: the background folder step, then the main-thread step (scenes generated, saved, reopened). */
    @Test
    @Order(1)
    fun CreateProject_SplitIntoBackgroundAndMainThreadSteps() {
        repeat(2) { run ->
            val label = if (run == 0) "cold" else "warm"
            val name = "Timing$run"
            val (folder, folderMs) = timed { projects.prepareProjectFolder(name, dir) }
            record("create project: copy assets (bg, $label)", folderMs)
            val (created, mainMs) = timed { projects.createProject(name, dir, preparedDir = folder.getOrThrow()) }
            assertTrue(created.isSuccess, created.exceptionOrNull()?.toString())
            record("create project: scenes (MAIN, fake assets, $label)", mainMs)
        }
        assertTrue(sceneManager.openScenes.any { it.name == FeatureShowcase.SCENE_NAME })
    }

    /** Play captures every open scene on the main thread; Stop decodes them back. Both must stay well under a second. */
    @Test
    @Order(2)
    fun PlayAndStop_SnapshotOfTheNewProjectScenes_StaysWithinBudget() {
        val open = sceneManager.openScenes.toList()
        var bestCapture = Double.MAX_VALUE
        var bestRestore = Double.MAX_VALUE
        repeat(5) {
            val (snapshots, captureMs) = timed { open.map { ScenePlaySnapshot.capture(it, serializer) } }
            val (_, restoreMs) = timed { snapshots.forEach { it.decode(serializer) } }
            bestCapture = minOf(bestCapture, captureMs)
            bestRestore = minOf(bestRestore, restoreMs)
        }
        record("play: capture ${open.size} open scenes (MAIN, best of 5)", bestCapture)
        record("stop: decode ${open.size} snapshots (MAIN, best of 5)", bestRestore)
        assertTrue(bestCapture < PLAY_CAPTURE_BUDGET_MS, "Play capture took $bestCapture ms")
        assertTrue(bestRestore < STOP_RESTORE_BUDGET_MS, "Stop restore took $bestRestore ms")
    }

    /** Open Scene decodes in the background; Save serializes and writes on the main thread. */
    @Test
    @Order(3)
    fun OpenAndSaveScene_ShowcaseTimings() {
        val file = File(dir, "Timing1/Scenes/${FeatureShowcase.SCENE_NAME}.scene")
        assertTrue(file.exists())
        report.appendLine("QA-19 showcase scene file: ${file.length() / 1024} KB")
        val (decoded, decodeMs) = timed { OpenSceneFileCommand.decode(file, serializer) }
        assertTrue(decoded.isSuccess)
        record("open scene: decode showcase (bg)", decodeMs)
        val showcase = sceneManager.openScenes.first { it.name == FeatureShowcase.SCENE_NAME }
        val saveDir = File(dir, "save").path
        val (_, saveMs) = timed { sceneManager.saveScene(showcase, saveDir) }
        record("save scene: showcase (MAIN)", saveMs)
    }

    /**
     * The real decodes behind the fakes: every texture and model the new project asked for. On the editor's main
     * thread these run inside the create step and again when a scene opens (until cached).
     */
    @Test
    @Order(4)
    fun RealAssetDecode_OfTheAssetsANewProjectLoads() {
        val textureLoader = TextureLoader()
        val assimp = AssimpLoader(textureLoader, VAOLoader())
        var textureTotal = 0.0
        val textureFiles = textures.mapNotNull(::resolve).distinctBy { it.name to it.length() }
        textureFiles.forEach { f ->
            val (_, ms) = timed { textureLoader.decodeFromFile(f.path) }
            textureTotal += ms
            record("decode texture ${f.name}", ms)
        }
        record("decode textures total (${textureFiles.size}, MAIN today)", textureTotal)
        var modelTotal = 0.0
        val modelFiles = models.mapNotNull(::resolve).distinctBy { it.name to it.length() }
        modelFiles.forEach { f ->
            val (scene, ms) = timed { assimp.importScene(f.path) }
            assimp.releaseScene(scene)
            modelTotal += ms
            record("import model ${f.name} (${f.length() / 1024} KB)", ms)
        }
        record("import models total (${modelFiles.size}, MAIN today)", modelTotal)
        report.appendLine("QA-19 unresolved asset keys: ${(textures + models).filter { resolve(it) == null }}")
        assertTrue(modelFiles.isNotEmpty(), "no model resolved from $models")
    }

    /** The showcase's seed script compiles with the Kotlin compiler on the first Play frame (main thread). */
    @Test
    @Order(5)
    fun ScriptCompile_ShowcaseSeedScript_ColdAndWarm() {
        val text = javaClass.getResource("/samples/showcase/${FeatureShowcase.SEED_SCRIPT}")!!.readText()
        val source = ScriptSource(FeatureShowcase.SEED_SCRIPT, text)
        val host = KotlinScriptHost()
        try {
            val (cold, coldMs) = timed { host.load(source) }
            record("script compile ${FeatureShowcase.SEED_SCRIPT} (MAIN, cold)", coldMs)
            assertTrue(cold is ScriptLoadResult.Success, "$cold")
            val (_, reloadMs) = timed { host.reload(source) }
            record("script compile ${FeatureShowcase.SEED_SCRIPT} (MAIN, warm compiler)", reloadMs)
            val (_, cachedMs) = timed { host.load(source) }
            record("script load ${FeatureShowcase.SEED_SCRIPT} (memory cache hit)", cachedMs)
        } finally {
            host.close()
        }
    }

    /** The generation part of the create step alone (default scene + showcase built and saved), warm. */
    @Test
    @Order(6)
    fun CreateProject_SceneGenerationAlone() {
        val (scenes, ms) = timed { prefabs.createDefaultScenes(File(dir, "generated")) }
        record("create project: generate+save ${scenes.size} scenes (MAIN, warm)", ms)
        assertTrue(scenes.any { it.name == FeatureShowcase.SCENE_NAME })
    }

    /** `engine:` keys live in the bundled `assets/` folder; project copies are absolute paths. */
    private fun resolve(key: String): File? {
        val candidates = listOf(File(key), File("assets", key.removePrefix("engine:")), File(key.removePrefix("assets/").let { "assets/$it" }))
        return candidates.firstOrNull { it.isFile }
    }

    private companion object {
        const val PLAY_CAPTURE_BUDGET_MS = 500.0
        const val STOP_RESTORE_BUDGET_MS = 500.0
    }
}
