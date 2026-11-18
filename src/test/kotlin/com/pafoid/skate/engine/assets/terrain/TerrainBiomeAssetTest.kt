package com.pafoid.skate.engine.assets.terrain

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.pack.PackManifest
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainLayer
import com.pafoid.skate.engine.ecs.systems.TerrainSystem
import com.pafoid.skate.engine.terrain.Biome
import com.pafoid.skate.engine.terrain.BiomeClimate
import com.pafoid.skate.engine.terrain.BiomePresets
import com.pafoid.skate.engine.terrain.BiomeSource
import com.pafoid.skate.engine.terrain.LayerRule
import com.pafoid.skate.engine.terrain.ScatterRule
import com.pafoid.skate.engine.utils.AssetsPacker
import com.pafoid.skate.engine.utils.AssetsResolver
import com.pafoid.skate.engine.utils.Atlas
import com.pafoid.skate.engine.utils.ExportOptions
import com.pafoid.skate.engine.utils.ExportTarget
import com.pafoid.skate.engine.utils.ProjectExporter
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

/** AST-38 (REN-28 §6, §7, §10): biomes, climate, painted biome ids and scatter references in `.skterrain` assets. */
class TerrainBiomeAssetTest {
    @TempDir
    lateinit var dir: File

    private val projectDir get() = File(dir, "Game").also { if (!it.isDirectory) ProjectLayout.create(it) }
    private val dataDir get() = File(projectDir, "Assets/Data")

    @BeforeEach
    fun setUp() { TerrainAssets.clock = { 0L }; TerrainAssets.source = TerrainAssetSource.NONE }

    @AfterEach
    fun tearDown() { TerrainAssets.source = TerrainAssetSource.NONE; TerrainAssets.clock = System::currentTimeMillis }

    /** 64 m, 16-quad chunks (4 × 4, region r_0_0), painted biomes, dense scatter so every chunk gets some. */
    private fun terrain(scatter: List<ScatterRule> = listOf(ScatterRule(id = "rocks", model = BiomePresets.ROCK_MODEL, densityPer100m2 = 4f, minSpacing = 1f, maxSlope = 90f))) =
        TerrainComponent(sizeMetres = 64f, chunkQuads = 16, seedName = "terrain/T", biomeSource = BiomeSource.PAINTED).apply {
            biomes.clear()
            biomes += Biome("Meadow", layerRules = listOf(LayerRule(BiomePresets.GRASS)))
            biomes += Biome("Quarry", layerRules = listOf(LayerRule(BiomePresets.ROCK)), scatter = scatter)
            climate = BiomeClimate(lapseRate = 0.05f, grid = listOf(0, 1, 0, 1, 0, 1, 0, 1, 0))
        }

    private fun build(t: TerrainComponent) {
        val scene = Scene("S").apply { addChild(GameObject("T").addComponent(t)) }
        TerrainSystem().apply { init(scene) }.update(0f)
    }

    /** Paints every own sample of chunk ([cx], [cz]) with [biome], as the editor brush would, then refreshes it. */
    private fun paint(t: TerrainComponent, cx: Int, cz: Int, biome: Int) {
        val c = t.runtime.chunk(cx, cz)!!
        c.paintedBiomeIds = ByteArray((c.chunkQuads + 1) * (c.chunkQuads + 1)) { biome.toByte() }
        com.pafoid.skate.engine.terrain.TerrainChunkBuilder.refreshDerived(t.runtime.generator!!, c)
        t.runtime.markEdited()
    }

    /** Same instances; positions and normals within the heights' uint16 quantisation (about 1 mm). */
    private fun assertSameScatter(expected: List<com.pafoid.skate.engine.terrain.ScatterInstance>, actual: List<com.pafoid.skate.engine.terrain.ScatterInstance>) {
        assertEquals(expected.map { it.rule to it.key }, actual.map { it.rule to it.key }, "same scatter instances")
        expected.zip(actual).forEach { (e, a) ->
            assertTrue(abs(e.x - a.x) < 1e-4f && abs(e.z - a.z) < 1e-4f && abs(e.y - a.y) < 0.01f && abs(e.scale - a.scale) < 1e-6f, "$e vs $a")
            assertTrue(abs(e.nx - a.nx) < 0.01f && abs(e.ny - a.ny) < 0.01f && abs(e.nz - a.nz) < 0.01f, "normal $e vs $a")
        }
    }

    private fun registrySource() = TerrainAssetSource.forRegistry(AssetRegistry(projectDir).also { it.scan() })

    @Test
    fun Document_StoresBiomesClimateAndSource_AndOldFilesGetTheDefaults() {
        val t = terrain()
        val doc = TerrainDocument.decode(TerrainDocument.of(t).encode())
        assertEquals(t.biomes, doc.biomes)
        assertEquals(t.climate, doc.climate)
        assertEquals(BiomeSource.PAINTED, doc.biomeSource)
        val copy = TerrainComponent().also { doc.applyTo(it) }
        assertEquals(t.key(5L), copy.key(5L), "the copy builds the same terrain, biomes included")

        val v1 = """{"schema":1,"sizeMetres":64.0,"chunkQuads":16}""" // an AST-36 file
        val old = TerrainDocument.decode(v1)
        assertEquals(BiomePresets.defaults(), old.biomes)
        assertEquals(BiomeSource.PROCEDURAL, old.biomeSource)
        assertEquals(BiomeClimate(), old.climate)
    }

    @Test
    fun Region_PaintedBiomesAreAnOptionalRecordSection() {
        val heights = FloatArray(19 * 19) { it * 0.01f }
        val painted = ByteArray(17 * 17) { (it % 3).toByte() }
        val bytes = TerrainRegionFile.encodeRecords(16, 1f, mapOf(0 to TerrainRegionFile.record(heights, painted), 1 to TerrainRegionFile.record(heights)))
        val (header, records) = TerrainRegionFile.readAll(bytes)
        assertEquals(TerrainRegionFile.FLAG_HEIGHTS or TerrainRegionFile.FLAG_PAINTED_BIOMES, header.entries[0].flags)
        assertEquals(TerrainRegionFile.FLAG_HEIGHTS, header.entries[1].flags)
        val withPaint = TerrainRegionFile.decodeChunk(records.getValue(0), 16)
        assertArrayEquals(painted, withPaint.paintedBiomeIds)
        assertTrue(heights.indices.all { abs(heights[it] - withPaint.heights[it]) < 1e-3f })
        assertNull(TerrainRegionFile.decodeChunk(records.getValue(1), 16).paintedBiomeIds)
        // A section this reader does not know (e.g. future splat bytes) is skipped.
        val future = TerrainRegionFile.Record(TerrainRegionFile.encodeRecord(heights) + ByteArray(40), TerrainRegionFile.FLAG_HEIGHTS or 64)
        assertNull(TerrainRegionFile.decodeChunk(future, 16).paintedBiomeIds)
    }

    @Test
    fun PaintedScatteredTerrain_SavesOnlyPaintedChunksAndReloadsIdentically() {
        val t = terrain()
        build(t)
        paint(t, 2, 1, 1)
        val authored = t.runtime.chunk(2, 1)!!
        assertTrue(authored.scatter.isNotEmpty(), "the quarry biome scatters rocks on the painted chunk")
        TerrainAssetStore.create(dataDir, "Painted", t)
        val header = TerrainRegionFile.readHeader(TerrainAssetStore(File(dataDir, "Painted.skterrain")).regionFile(0, 0).readBytes())
        assertEquals(listOf(TerrainRegionFile.slotOf(2, 1)), header.entries.indices.filter { header.entries[it].present },
            "only the painted chunk is stored: the others still follow the recipe")

        TerrainAssets.source = registrySource()
        val loaded = TerrainComponent(asset = t.asset)
        build(loaded)
        assertEquals(t.biomes, loaded.biomes); assertEquals(t.climate, loaded.climate); assertEquals(BiomeSource.PAINTED, loaded.biomeSource)
        val chunk = loaded.runtime.chunk(2, 1)!!
        assertArrayEquals(authored.paintedBiomeIds, chunk.paintedBiomeIds)
        assertArrayEquals(authored.biomeIds, chunk.biomeIds, "biome ids recomputed from the painted ids")
        assertArrayEquals(authored.splat, chunk.splat)
        assertSameScatter(authored.scatter, chunk.scatter)
        assertNull(loaded.runtime.chunk(0, 0)!!.paintedBiomeIds)
    }

    @Test
    fun SculptedChunk_RefreshesSplatAndScatterFromTheLoadedHeights() {
        val t = terrain()
        build(t)
        paint(t, 0, 0, 1)
        val c = t.runtime.chunk(0, 0)!!
        for (k in c.heights.indices) c.heights[k] += 30f // a tall plateau: different slopes at its edge
        com.pafoid.skate.engine.terrain.TerrainChunkBuilder.refreshDerived(t.runtime.generator!!, c)
        TerrainAssetStore.create(dataDir, "Sculpted", t)

        TerrainAssets.source = registrySource()
        val loaded = TerrainComponent(asset = t.asset)
        build(loaded)
        val l = loaded.runtime.chunk(0, 0)!!
        assertTrue(l.heights.indices.all { abs(l.heights[it] - c.heights[it]) < 2e-3f })
        assertEquals(c.scatter.size, l.scatter.size, "scatter placed on the loaded heights, not the generated ones")
        assertTrue(l.scatter.zip(c.scatter).all { (a, b) -> abs(a.y - b.y) < 0.01f })
    }

    private fun project(sceneText: String): Project {
        val scene = File(ProjectLayout.scenesDir(projectDir).also { it.mkdirs() }, "Main.scene").also { it.writeText(sceneText) }
        val file = File(projectDir, "Game.skateproject")
        val p = Project(name = "Game", projectPath = file.absolutePath, defaultScene = scene.absolutePath, scenesPath = listOf(scene.absolutePath))
        file.writeText(Serializer().encodeFile(FileKind.PROJECT, p))
        return p
    }

    @Test
    fun Export_FollowsLayerMaterialsScatterModelsRuleMaterialsAndPrefabs() {
        fun file(rel: String, text: String) = File(projectDir, rel).apply { parentFile.mkdirs(); writeText(text) }
        file("Assets/Materials/grass.skmat", "{}")
        file("Assets/Materials/bark.skmat", "{}")
        file("Assets/Models/tree.glb", "glb")
        file("Assets/Models/bush.glb", "glb")
        file("Assets/Prefabs/bush.prefab", """{"schema":1,"id":"bush","template":{"model":"Assets/Models/bush.glb"}}""")
        file("Assets/Models/unused.glb", "glb")
        val registry = AssetRegistry(projectDir).also { it.scan() }
        fun guid(rel: String) = registry.idOf(rel)!!.value

        val t = terrain(scatter = listOf(
            ScatterRule(id = "trees", model = guid("Assets/Models/tree.glb"), material = guid("Assets/Materials/bark.skmat"), densityPer100m2 = 2f),
            ScatterRule(id = "bushes", prefab = "Assets/Prefabs/bush.prefab", densityPer100m2 = 2f),
            ScatterRule(id = "rocks", model = BiomePresets.ROCK_MODEL, densityPer100m2 = 1f),
        )).apply { layers[BiomePresets.GRASS] = TerrainLayer(material = guid("Assets/Materials/grass.skmat")) }
        build(t)
        paint(t, 1, 1, 1)
        TerrainAssetStore.create(dataDir, "Island", t)
        val p = project("""{"children":[{"components":[{"type":"TerrainComponent","asset":"${t.asset}"}]}]}""")

        val out = File(dir, "out")
        assertTrue(ProjectExporter(mockk(relaxed = true)).export(p, ExportOptions(target = ExportTarget.PACK_ONLY, outputDir = out.path)).success)
        val paths = Serializer().json.decodeFromString(PackManifest.serializer(), File(out, AssetsPacker.MANIFEST_FILE).readText()).entries.map { it.path }.toSet()
        val expected = listOf(
            "Assets/Data/Island.skterrain/terrain.json", "Assets/Data/Island.skterrain/regions/r_0_0.bin",
            "Assets/Materials/grass.skmat", "Assets/Materials/bark.skmat", "Assets/Models/tree.glb",
            "Assets/Prefabs/bush.prefab", "Assets/Models/bush.glb", BiomePresets.ROCK_MODEL,
        )
        assertEquals(emptyList<String>(), expected.filterNot { it in paths }, "references missing from the pack: $paths")
        assertTrue("Assets/Models/unused.glb" !in paths)

        // The exported game reloads the painted chunk from the pack.
        val bin = File(out, AssetsPacker.BIN_FILE).readBytes()
        val headerSize = ByteBuffer.wrap(bin, 0, 4).int + 4
        val atlas = Serializer().decode<Atlas>(bin.copyOfRange(4, headerSize).toString(Charsets.UTF_8))
        TerrainAssets.source = TerrainAssetSource.forPack(AssetPaths().apply {
            pack = AssetsResolver(Serializer(), LoggerService()).apply { initialize(atlas, bin, headerSize) }
            engineDefaults = EngineDefaultResolver(File(dir, "no-engine"))
        })
        val game = TerrainComponent(asset = t.asset)
        build(game)
        assertNotNull(game.runtime.chunk(1, 1)!!.paintedBiomeIds)
        assertSameScatter(t.runtime.chunk(1, 1)!!.scatter, game.runtime.chunk(1, 1)!!.scatter)
    }
}
