package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.terrain.BiomeClimate
import com.pafoid.skate.engine.terrain.BiomePresets
import com.pafoid.skate.engine.terrain.TerrainChunkBuilder
import com.pafoid.skate.engine.terrain.TerrainGenerator
import com.pafoid.skate.engine.terrain.TerrainLayout
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.utils.AssetsPacker
import com.pafoid.skate.engine.utils.AssetsResolver
import com.pafoid.skate.engine.utils.Atlas
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

/** REN-43: the default terrain scatter rules render grey rocks (engine default `.skmat`), in the editor and exports. */
class RockScatterMaterialTest {
    @TempDir
    lateinit var dir: File

    private val device = FakeRhiDevice()
    private val engineRoot = File(System.getProperty("user.dir"))
    private val resolver = EngineDefaultResolver(engineRoot)

    @BeforeEach fun setUp() { RhiContext.device = device }
    @AfterEach fun tearDown() { RhiContext.device = null }

    private fun rock(readText: (String) -> String?): Material =
        MaterialAsset.fromJson(readText(EngineDefaults.Materials.ROCK)!!).toMaterial { null }

    private fun assertGrey(m: Material) {
        val c = m.baseColor
        assertTrue(c.x in 0.3f..0.6f && kotlin.math.abs(c.x - c.y) < 0.05f && kotlin.math.abs(c.y - c.z) < 0.05f, "grey, got $c")
        assertEquals(0f, m.metallicFactor)
        assertTrue(m.roughnessFactor > 0.8f, "rough stone")
    }

    @Test
    fun RockMaterial_IsAGreyRoughEngineDefault_UsedByBothDefaultRules() {
        val file = resolver.resolve(EngineDefaults.Materials.ROCK)
        assertNotNull(file, "assets/Materials/rock.skmat ships with the engine")
        assertGrey(rock { resolver.resolve(it)?.readText() })
        assertTrue(EngineDefaults.Materials.ROCK in EngineDefaults.CATALOG, "copied into projects in copy mode, GUID derivable")
        val texture = MaterialAsset.fromJson(file!!.readText()).textures.values.single()
        assertNotNull(resolver.resolve(texture), "its texture is an engine default too")
        val rules = BiomePresets.defaults().flatMap { it.scatter }
        assertEquals(setOf("rocks", "boulders"), rules.filter { it.model == BiomePresets.ROCK_MODEL }.map { it.id }.toSet())
        assertTrue(rules.filter { it.model == BiomePresets.ROCK_MODEL }.all { it.material == EngineDefaults.Materials.ROCK })
    }

    @Test
    fun Scatter_DefaultRocksDrawWithTheRockMaterial_NotTheSpheresWhite() {
        val loader = VAOLoader { device }
        val part = loader.loadToVAO(FloatArray(9), FloatArray(6), FloatArray(9), intArrayOf(0, 1, 2))
        val white = part.material // the model's own (default white) material
        val sphere = `3dModel`(mesh = listOf(part))
        val renderer = TerrainScatterRenderer(ModelRenderer(mockk(relaxed = true), { device }, loader), { sphere }, { path ->
            MaterialAsset.fromJson(resolver.resolve(path)!!.readText()).toMaterial { null } // what RenderResourcesFactory does via AssetPaths
        }) { device }
        val grassland = BiomePresets.defaults()[0]
        val g = TerrainGenerator(TerrainPreset.FLAT.recipe(), emptyList(), TerrainLayout(128f, 1f, 64), 30f, 1L, "t", 0, listOf(grassland), BiomeClimate())
        val chunk = TerrainChunkBuilder.build(g, 0, 0)
        assertTrue(chunk.scatter.isNotEmpty(), "the default rocks scatter on flat grassland")
        val batch = renderer.batches(chunk, g.scatterEntries, Vector3f()).single().batches.single()
        assertTrue(batch.material !== white, "the rule's material overrides the model's")
        assertGrey(batch.material)
        renderer.destroy()
    }

    @Test
    fun Export_PacksTheRockMaterialAndItsTexture_AndTheGameReadsIt() {
        val projectDir = File(dir, "Game").also { ProjectLayout.create(it) }
        val paths = AssetPaths().apply { projectRoot = projectDir.absoluteFile; engineDefaults = EngineDefaultResolver(engineRoot) { projectRoot } }
        val scene = Scene("Main").apply { addChild(GameObject("Terrain").addComponent(Transform()).addComponent(TerrainComponent())) }
        val sceneFile = File(ProjectLayout.scenesDir(projectDir), "Main.scene").apply { writeText(Serializer(paths).encodeFile(FileKind.SCENE, scene)) }
        val file = File(projectDir, "Game.skateproject")
        val p = Project(name = "Game", projectPath = file.absolutePath, defaultScene = sceneFile.absolutePath, scenesPath = listOf(sceneFile.absolutePath))
        file.writeText(Serializer().encodeFile(FileKind.PROJECT, p))
        assertTrue(EngineDefaults.Materials.ROCK in sceneFile.readText(), "a default terrain's scatter rules name the rock material")

        val out = File(dir, "out")
        assertTrue(AssetsPacker(LoggerService(), File(dir, "no-strings"), engineRoot).pack(p, outputDir = out))
        val bin = File(out, AssetsPacker.BIN_FILE).readBytes()
        val header = ByteBuffer.wrap(bin, 0, 4).int + 4
        val atlas = Serializer().decode<Atlas>(bin.copyOfRange(4, header).toString(Charsets.UTF_8))
        val packed = atlas.values.flatten().map { it.path }.toSet()
        assertTrue(EngineDefaults.Materials.ROCK in packed && EngineDefaults.Textures.CONCRETE_SIMPLE in packed && BiomePresets.ROCK_MODEL in packed, "$packed")

        val game = AssetPaths().apply {
            pack = AssetsResolver(Serializer(), LoggerService()).apply { initialize(atlas, bin, header) }
            engineDefaults = EngineDefaultResolver(File(dir, "no-engine"))
        }
        assertGrey(rock { game.readText(it) })
    }
}
