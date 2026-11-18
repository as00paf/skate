package com.pafoid.skate.engine.assets.terrain

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.pack.PackManifest
import com.pafoid.skate.engine.assets.pack.PackedGuids
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainLayer
import com.pafoid.skate.engine.ecs.components.TerrainSource
import com.pafoid.skate.engine.ecs.systems.TerrainSystem
import com.pafoid.skate.engine.terrain.StampMode
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainStamp
import com.pafoid.skate.engine.utils.AssetsPacker
import com.pafoid.skate.engine.utils.AssetsResolver
import com.pafoid.skate.engine.utils.Atlas
import com.pafoid.skate.engine.utils.ExportOptions
import com.pafoid.skate.engine.utils.ExportTarget
import com.pafoid.skate.engine.utils.ProjectExporter
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

/** AST-36 (REN-28 §10, §15.6): `.skterrain` save, reload, hot reload and export. */
class TerrainAssetTest {
    @TempDir
    lateinit var dir: File

    private var now = 0L
    private val projectDir get() = File(dir, "Game").also { if (!it.isDirectory) ProjectLayout.create(it) }
    private val dataDir get() = File(projectDir, "Assets/Data")

    @BeforeEach
    fun setUp() {
        TerrainAssets.clock = { now }
        TerrainAssets.source = TerrainAssetSource.NONE
    }

    @AfterEach
    fun tearDown() {
        TerrainAssets.source = TerrainAssetSource.NONE
        TerrainAssets.clock = System::currentTimeMillis
    }

    /** 64 m, 16-quad chunks: 4 × 4 chunks, all in region r_0_0. */
    private fun terrain(source: TerrainSource = TerrainSource.PROCEDURAL) = TerrainComponent(
        sizeMetres = 64f, chunkQuads = 16, source = source, seedName = "terrain/T",
        stamps = mutableListOf(TerrainStamp(x = 10f, z = -10f, y = 3f, mode = StampMode.FLATTEN)),
    )

    private fun system(vararg terrains: TerrainComponent): TerrainSystem {
        val scene = Scene("S")
        terrains.forEachIndexed { i, t -> scene.addChild(GameObject("T$i").addComponent(t)) }
        // No rebuild debounce: these tests step with dt = 0 (REN-42 debounces inspector drags by 0.25 s)
        return TerrainSystem(debounceSeconds = 0f).apply { init(scene) }
    }

    /** Raises chunk ([cx], [cz]) by [dy] m everywhere (apron included), like a sculpt brush would. */
    private fun sculpt(t: TerrainComponent, cx: Int, cz: Int, dy: Float) {
        val c = t.runtime.chunk(cx, cz)!!
        for (k in c.heights.indices) c.heights[k] += dy
        t.runtime.markEdited()
    }

    private fun assertClose(expected: FloatArray, actual: FloatArray, tolerance: Float = 2e-3f) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertTrue(abs(expected[it] - actual[it]) <= tolerance, "sample $it: ${expected[it]} vs ${actual[it]}") }
    }

    private fun registrySource(): TerrainAssetSource = TerrainAssetSource.forRegistry(AssetRegistry(projectDir).also { it.scan() })

    // --- Format ------------------------------------------------------------------------------------------------

    @Test
    fun Region_RoundTripsRecordsWithTheIndexAndChecksTheirCrc() {
        val a = FloatArray(19 * 19) { it * 0.01f - 1f }
        val b = FloatArray(19 * 19) { 5f } // flat: step 0
        val bytes = TerrainRegionFile.encode(16, 1f, mapOf(TerrainRegionFile.slotOf(1, 2) to TerrainRegionFile.encodeRecord(a), 255 to TerrainRegionFile.encodeRecord(b)))
        val header = TerrainRegionFile.readHeader(bytes)
        assertEquals(16, header.chunkQuads); assertEquals(1f, header.sampleSpacing)
        assertEquals(2, header.entries.count { it.present })
        assertTrue(header.entries[255].storedSize < header.entries[255].rawSize / 10, "records are deflated (flat ground: tiny)")
        assertClose(a, TerrainRegionFile.decodeRecord(TerrainRegionFile.readRecord(bytes, header, TerrainRegionFile.slotOf(1, 2))!!, a.size))
        assertClose(b, TerrainRegionFile.decodeRecord(TerrainRegionFile.readRecord(bytes, header, 255)!!, b.size), 0f)
        assertNull(TerrainRegionFile.readRecord(bytes, header, 0))
        assertEquals(TerrainRegionFile.slotOf(17, 34), TerrainRegionFile.slotOf(1, 2), "slot is the position inside its region")
        assertEquals(-1, TerrainRegionFile.regionOf(-1))

        val corrupt = bytes.copyOf().also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() }
        assertThrows<IllegalArgumentException> { TerrainRegionFile.readAll(corrupt) }
        assertThrows<IllegalArgumentException> { TerrainRegionFile.readHeader(ByteArray(TerrainRegionFile.HEADER_BYTES)) }
    }

    @Test
    fun Document_RoundTripsRecipeStampsAndLayers_AndRefusesOtherSchemas() {
        val t = terrain().apply {
            recipe = TerrainPreset.MOUNTAINS.recipe()
            layers[0] = TerrainLayer(material = "0123456789abcdef0123456789abcdef", tilingMetres = 3f)
        }
        val doc = TerrainDocument.decode(TerrainDocument.of(t).encode())
        assertEquals(TerrainDocument.of(t), doc)
        val copy = TerrainComponent().also { doc.applyTo(it) }
        assertEquals(t.key(7L), copy.key(7L), "the copy generates the same terrain")
        assertEquals(t.layers, copy.layers)
        assertThrows<IllegalArgumentException> { TerrainDocument.decode("""{"schema":99}""") }
    }

    // --- Save and reload ---------------------------------------------------------------------------------------

    @Test
    fun Save_ProceduralStoresOnlySculptedChunks_AndDropsThemWhenUnsculpted() {
        val t = terrain()
        system(t).update(0f)
        sculpt(t, 1, 2, 4f)
        val folder = TerrainAssetStore.create(dataDir, "Island", t)
        assertEquals("Island.skterrain", folder.name)
        assertEquals(AssetMeta.read(folder)!!.guid.value, t.asset, "the component references the folder's GUID")
        val store = TerrainAssetStore(folder)
        assertTrue(store.documentFile.isFile)
        val region = store.regionFile(0, 0)
        assertTrue(region.isFile)
        val header = TerrainRegionFile.readHeader(region.readBytes())
        assertEquals(listOf(TerrainRegionFile.slotOf(1, 2)), header.entries.indices.filter { header.entries[it].present }, "only the sculpted chunk")
        assertClose(t.runtime.chunk(1, 2)!!.heights, store.readChunk(1, 2, 16, 1f)!!.heights)
        assertFalse(folder.walkTopDown().any { it.name.endsWith(".tmp") }, "atomic writes leave no temp files")

        sculpt(t, 1, 2, -4f)
        val result = store.save(t)
        assertEquals(1, result.chunksRemoved)
        assertFalse(region.exists(), "an empty region is deleted: unedited procedural terrains store only the recipe")
    }

    @Test
    fun Reload_ASculptedTerrainComesBackFromItsAsset() {
        val t = terrain()
        system(t).update(0f)
        sculpt(t, 3, 0, 6f)
        TerrainAssetStore.create(dataDir, "Island", t)
        val sculpted = t.runtime.chunk(3, 0)!!.heights.copyOf()
        val untouched = t.runtime.chunk(0, 0)!!.heights.copyOf()

        TerrainAssets.source = registrySource()
        // A scene saved with only the GUID (other settings at their defaults) reloads the whole terrain.
        val loaded = TerrainComponent(asset = t.asset)
        system(loaded).update(0f)
        assertEquals(64f, loaded.sizeMetres); assertEquals(16, loaded.chunkQuads)
        assertEquals(t.stamps, loaded.stamps)
        assertClose(sculpted, loaded.runtime.chunk(3, 0)!!.heights)
        assertClose(untouched, loaded.runtime.chunk(0, 0)!!.heights, 0f)
        val v = loaded.runtime.version
        system(loaded).update(0f)
        assertEquals(v, loaded.runtime.version, "nothing reloads while the asset is unchanged")
    }

    @Test
    fun Data_TerrainIsFlatWithoutChunkData_AndLoadsItsRegions() {
        TerrainAssets.source = registrySource()
        val empty = terrain(TerrainSource.DATA).apply { stamps.clear() }
        system(empty).update(0f)
        assertTrue(empty.runtime.chunks.all { c -> c.heights.all { it == 0f } }, "DATA without data renders flat")

        val t = terrain(TerrainSource.DATA).apply { stamps.clear() }
        system(t).update(0f)
        sculpt(t, 0, 0, 2f); sculpt(t, 2, 3, -1f)
        TerrainAssetStore.create(dataDir, "Flat", t)
        val header = TerrainRegionFile.readHeader(TerrainAssetStore(File(dataDir, "Flat.skterrain")).regionFile(0, 0).readBytes())
        assertEquals(16, header.entries.count { it.present }, "a DATA terrain stores every chunk")

        TerrainAssets.source = registrySource()
        val loaded = TerrainComponent(asset = t.asset)
        system(loaded).update(0f)
        assertEquals(TerrainSource.DATA, loaded.source)
        assertClose(t.runtime.chunk(0, 0)!!.heights, loaded.runtime.chunk(0, 0)!!.heights)
        assertClose(t.runtime.chunk(2, 3)!!.heights, loaded.runtime.chunk(2, 3)!!.heights)
    }

    // --- Hot reload --------------------------------------------------------------------------------------------

    @Test
    fun HotReload_DocumentAndRegionChangesReachTheLiveTerrain() {
        val author = terrain()
        system(author).update(0f)
        val folder = TerrainAssetStore.create(dataDir, "Island", author)
        TerrainAssets.source = registrySource()
        val live = TerrainComponent(asset = author.asset)
        val sys = system(live)
        sys.update(0f)
        val before = live.runtime.chunk(1, 1)!!.heights.copyOf()

        // Another process (or a text editor) changes terrain.json: the terrain rebuilds with it.
        val store = TerrainAssetStore(folder)
        store.saveDocument(store.loadDocument().copy(heightScaleMetres = 10f))
        now += 1000; sys.update(0f) // change seen, waiting to be stable
        assertEquals(64f, live.heightScaleMetres)
        now += 1000; sys.update(0f) // stable: reloaded
        assertEquals(10f, live.heightScaleMetres)
        assertEquals(1, sys.rebuilt)

        // A region file appears (sculpted elsewhere): the resident chunk reloads without a rebuild.
        author.heightScaleMetres = 10f
        system(author).update(0f)
        sculpt(author, 1, 1, 5f)
        store.save(author)
        now += 1000; sys.update(0f)
        now += 1000; sys.update(0f)
        assertEquals(0, sys.rebuilt)
        assertClose(author.runtime.chunk(1, 1)!!.heights, live.runtime.chunk(1, 1)!!.heights)

        // The record is removed again: the chunk goes back to generated heights.
        sculpt(author, 1, 1, -5f)
        store.save(author)
        now += 1000; sys.update(0f)
        now += 1000; sys.update(0f)
        assertClose(author.runtime.chunk(1, 1)!!.heights, live.runtime.chunk(1, 1)!!.heights, 1e-4f)
        assertFalse(before.contentEquals(live.runtime.chunk(1, 1)!!.heights), "height scale changed in between")
    }

    @Test
    fun Save_ThroughTerrainAssetsIsNotReloadedOverUnsavedState() {
        val t = terrain()
        system(t).update(0f)
        TerrainAssetStore.create(dataDir, "Island", t)
        TerrainAssets.source = registrySource()
        val sys = system(t)
        sys.update(0f)
        sculpt(t, 0, 1, 3f)
        assertNotNull(TerrainAssets.save(t))
        val saved = t.runtime.chunk(0, 1)!!.heights.copyOf()
        now += 1000; sys.update(0f)
        now += 1000; sys.update(0f)
        assertTrue(saved.contentEquals(t.runtime.chunk(0, 1)!!.heights), "own save does not reload quantised heights over the live ones")
    }

    // --- Registry, packing, export -----------------------------------------------------------------------------

    @Test
    fun Registry_RegistersTheFolderAsOneAsset() {
        val t = terrain()
        system(t).update(0f)
        sculpt(t, 0, 0, 1f)
        val folder = TerrainAssetStore.create(dataDir, "Island", t)
        val registry = AssetRegistry(projectDir).also { it.scan() }
        assertEquals("Assets/Data/Island.skterrain", registry.resolve(t.asset))
        assertTrue(folder.walkTopDown().none { AssetMeta.isSidecar(it) }, "no .meta inside the asset folder")
    }

    private fun project(sceneText: String): Project {
        val scene = File(ProjectLayout.scenesDir(projectDir).also { it.mkdirs() }, "Main.scene").also { it.writeText(sceneText) }
        val file = File(projectDir, "Game.skateproject")
        val p = Project(name = "Game", projectPath = file.absolutePath, defaultScene = scene.absolutePath, scenesPath = listOf(scene.absolutePath))
        file.writeText(Serializer().encodeFile(FileKind.PROJECT, p))
        return p
    }

    @Test
    fun Export_PacksTheReferencedTerrainAndTheGameLoadsIt() {
        val t = terrain()
        system(t).update(0f)
        sculpt(t, 2, 1, 7f)
        val material = File(projectDir, "Assets/Materials/rock.skmat").also { it.parentFile.mkdirs(); it.writeText("{}") }
        val materialGuid = AssetRegistry(projectDir).also { it.scan() }.idOf("Assets/Materials/rock.skmat")!!.value
        t.layers[2] = TerrainLayer(material = materialGuid)
        TerrainAssetStore.create(dataDir, "Island", t)
        File(dataDir, "Unused.skterrain").also { TerrainAssetStore(it).saveDocument(TerrainDocument()) }
        val p = project("""{"children":[{"components":[{"type":"TerrainComponent","asset":"${t.asset}"}]}]}""")

        val out = File(dir, "out")
        val result = ProjectExporter(mockk(relaxed = true)).export(p, ExportOptions(target = ExportTarget.PACK_ONLY, outputDir = out.path))
        assertTrue(result.success, "$result")
        val manifest = Serializer().json.decodeFromString(PackManifest.serializer(), File(out, AssetsPacker.MANIFEST_FILE).readText())
        val paths = manifest.entries.associateBy { it.path }
        assertNotNull(paths["Assets/Data/Island.skterrain/terrain.json"])
        val region = paths.getValue("Assets/Data/Island.skterrain/regions/r_0_0.bin")
        assertFalse(region.compressed, "region records are already deflated")
        assertNotNull(paths["Assets/Materials/rock.skmat"], "layer materials follow the terrain")
        assertTrue(paths.keys.none { it.contains("Unused.skterrain") }, "unreferenced terrains stay out")

        // The exported game: the GUID resolves through the pack table, the data comes from the pack.
        val bin = File(out, AssetsPacker.BIN_FILE).readBytes()
        val headerSize = ByteBuffer.wrap(bin, 0, 4).int + 4
        val atlas = Serializer().decode<Atlas>(bin.copyOfRange(4, headerSize).toString(Charsets.UTF_8))
        val paths2 = AssetPaths().apply {
            pack = AssetsResolver(Serializer(), LoggerService()).apply { initialize(atlas, bin, headerSize) }
            engineDefaults = EngineDefaultResolver(File(dir, "no-engine"))
        }
        assertEquals("Assets/Data/Island.skterrain", PackedGuids.read(paths2)[t.asset])
        TerrainAssets.source = TerrainAssetSource.forPack(paths2)
        val game = TerrainComponent(asset = t.asset)
        system(game).update(0f)
        assertClose(t.runtime.chunk(2, 1)!!.heights, game.runtime.chunk(2, 1)!!.heights)
        assertEquals(t.stamps, game.stamps)
    }
}
