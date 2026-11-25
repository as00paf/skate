package com.pafoid.skate.game

import com.pafoid.skate.templates.NewProjectSettings
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.JobSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.AssetsResolver
import com.pafoid.skate.engine.utils.ExportOptions
import com.pafoid.skate.engine.utils.ExportTarget
import com.pafoid.skate.engine.utils.ProjectExporter
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lwjgl.assimp.Assimp
import org.lwjgl.system.MemoryUtil
import java.io.File

/**
 * AST-39 regression (P0, human round 35): a NEW default-template project exported with the default options must
 * start. The exported game resolves every top-level RenderComponent model (and Animator clip) of the default scene
 * from game.bin and imports it with Assimp from memory, as `ProjectManager.openProject` does in `GameMain`. With an
 * empty format hint the engine's `rail.obj` (200+ bytes of comments first) failed with
 * "No suitable reader found … $$$___magic___$$$." and the game crashed at start-up.
 *
 * The other lifecycle tests stub `AssetsManager.resolveModel`, so no packed model was ever parsed before.
 */
class ExportedGameModelsTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun DefaultTemplateExport_EveryModelAndClipImportsFromThePack() {
        val logger = LoggerService().also { it.consoleSink = { _, _ -> } }
        val serializer = Serializer()
        val events = EventSystem(logger)
        val systems = SystemManager()
        // No GL: the editor side never builds meshes or textures here (as in ProjectLifecycleEndToEndTest).
        val assets = spyk(AssetsManager(serializer, logger)).also { a ->
            every { a.loadModel(any<String>()) } answers { `3dModel`(firstArg(), skeleton = TestHumanoid.skeleton()) }
            every { a.getTexture(any()) } answers { Texture(filePath = firstArg()) }
            every { a.loadAnimationSync(any()) } answers { firstArg<String>().let { Animation(AnimationLoader.clipName(it), emptyList(), 1f, it) } }
            every { a.getSound(any()) } answers { SoundBuffer(firstArg()).apply { bufferId = 1 } }
        }
        val sceneManager = SceneManager(assets, events, serializer, systems, logger)
        val engine = mockk<Engine>(relaxed = true).also {
            every { it.assetsManager } returns assets
            every { it.sceneManager } returns sceneManager
            every { it.serializer } returns serializer
            every { it.eventSystem } returns events
            every { it.logger } returns logger
            every { it.systemManager } returns systems
            every { it.jobSystem } returns JobSystem(logger = logger)
        }
        every { engine.prefabsGenerator } returns PrefabsGenerator(engine)

        // File > New Project (default template, defaults copied), then File > Export with the default options.
        val projects = ProjectManager(engine)
        val created = projects.createProject("Repro", File(dir, "Workspace").also { it.mkdirs() }, NewProjectSettings(template = "feature-showcase"))
        assertTrue(created.isSuccess, created.exceptionOrNull()?.toString())
        val project = projects.currentProject!!
        val out = File(dir, "Export")
        val result = ProjectExporter(logger).export(project, ExportOptions(target = ExportTarget.PACK_ONLY, outputDir = out.path))
        assertTrue(result.success, "$result")
        projects.closeProject()

        // GameMain: read game.bin, then the default scene from the pack.
        val bin = File(out, "game.bin").readBytes()
        val (atlas, headerSize) = readFileHeader(bin)
        val (packed, _) = loadProjectAndIcon(bin, atlas!!, headerSize)
        val resolver = AssetsResolver(serializer, logger).apply { initialize(atlas, bin, headerSize) }
        val scene = resolver.resolve<Scene>(packed!!.defaultScene)
        assertNotNull(scene, "default scene in the pack")

        val models = scene!!.children.mapNotNull { it.getComponent<RenderComponent>()?.model?.path }.distinct()
        val clips = scene.children.flatMap { go -> go.getComponent<Animator>()?.animations.orEmpty().map { it.path } }.distinct()
        assertTrue(EngineDefaults.Models.RAIL in models, "the default scene has the rail that crashed: $models")
        val failures = (models.map { it to AssimpLoader.IMPORT_FLAGS } + clips.map { it to AnimationLoader.IMPORT_FLAGS }).mapNotNull { (path, flags) ->
            val bytes = resolver.resolveBytes(path) ?: return@mapNotNull "$path: not in the pack"
            val buffer = MemoryUtil.memAlloc(bytes.size).put(bytes).flip()
            try {
                val imported = AssimpLoader.importFromMemory(buffer, path, flags)
                if (imported == null) "$path: ${Assimp.aiGetErrorString()}" else null.also { Assimp.aiReleaseImport(imported) }
            } finally {
                MemoryUtil.memFree(buffer)
            }
        }
        assertEquals(emptyList<String>(), failures, "models and clips the exported game cannot load")
        assertTrue(models.size >= 5, "default scene models: $models")
    }

    @Test
    fun FormatHint_IsTheLowercasedExtension() {
        assertEquals("obj", AssimpLoader.formatHint(EngineDefaults.Models.RAIL))
        assertEquals("glb", AssimpLoader.formatHint("Assets\\Models\\James.GLB"))
        assertEquals("fbx", AssimpLoader.formatHint("engine:Animations/idle phone.fbx"))
        assertEquals("", AssimpLoader.formatHint("Models/noext"))
        assertEquals("", AssimpLoader.formatHint("folder.v2/noext"), "a dot in a folder name is not an extension")
    }
}
