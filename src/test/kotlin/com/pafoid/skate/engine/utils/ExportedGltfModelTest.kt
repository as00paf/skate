package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.loaders.GltfFixture
import com.pafoid.skate.engine.assets.loaders.TextureLoader
import com.pafoid.skate.engine.assets.pack.PackManifest
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

/**
 * AST-22: a store-imported glTF (buffer next to it, textures in `textures/`) is packed with every file it names, and the
 * exported game extracts and loads it textured from the pack alone.
 */
class ExportedGltfModelTest {
    @TempDir
    lateinit var dir: File

    private val project get() = File(dir, "Game")
    private val modelDir get() = File(project, "Assets/Store/polyhaven/table")

    private fun saveProject(): Project {
        val gltf = GltfFixture.write(modelDir, "table", "textures/table_diff_1k.png")
        File(modelDir, "textures/unused_4k.png").writeBytes(GltfFixture.png())
        val scene = Scene("Main").apply { addChild(GameObject("Table").addComponent(RenderComponent(`3dModel`(gltf.absolutePath)))) }
        val sceneFile = File(project, "Scenes/Main.scene").also { it.parentFile.mkdirs() }
        sceneFile.writeText(Serializer(AssetPaths().apply { projectRoot = project.absoluteFile }).encodeFile(FileKind.SCENE, scene))
        val file = File(project, "Game.skateproject").also { it.writeText("{}") }
        return Project(name = "Game", projectPath = file.absolutePath, defaultScene = sceneFile.absolutePath, scenesPath = listOf(sceneFile.absolutePath))
    }

    private fun export(): Pair<AssetPaths, List<String>> {
        assertTrue(ProjectExporter(mockk<LoggerService>(relaxed = true), File(dir, "no-strings")).export(saveProject(), ExportOptions(target = ExportTarget.PACK_ONLY)).success)
        val builds = File(project, AssetsPacker.BUILDS_DIR)
        val bin = File(builds, AssetsPacker.BIN_FILE).readBytes()
        val manifest = Serializer().json.decodeFromString(PackManifest.serializer(), File(builds, "manifest.json").readText())
        project.deleteRecursively()
        val header = ByteBuffer.wrap(bin, 0, 4).int + 4
        val atlas = Serializer().decode<Atlas>(bin.copyOfRange(4, header).toString(Charsets.UTF_8))
        val resolver = AssetsResolver(Serializer(), mockk(relaxed = true)).also { it.initialize(atlas, bin, header) }
        return AssetPaths().apply { pack = resolver } to manifest.entries.map { it.path }
    }

    @Test
    fun Export_PacksTheGltfBufferAndSubfolderTextures() {
        val (_, packed) = export()

        listOf("table.gltf", "table.bin", "textures/table_diff_1k.png").forEach {
            assertTrue("Assets/Store/polyhaven/table/$it" in packed, "$it missing from $packed")
        }
        assertFalse("Assets/Store/polyhaven/table/textures/unused_4k.png" in packed, "only files the glTF names")
    }

    @Test
    fun ExportedGame_LoadsTheModelTexturedFromThePack() {
        val (paths, _) = export()
        val local = File(paths.localPath("Assets/Store/polyhaven/table/table.gltf"))
        assertTrue(File(local.parentFile, "textures/table_diff_1k.png").isFile, "subfolder texture extracted next to the model")

        val previous = RhiContext.device
        RhiContext.device = FakeRhiDevice()
        try {
            val texture = AssimpLoader(TextureLoader(), VAOLoader()).loadModel(local.absolutePath).mesh.single().material.baseColorTexture
            assertEquals(GltfFixture.TEXTURE_SIZE, texture?.width)
        } finally {
            RhiContext.device = previous
        }
    }
}
