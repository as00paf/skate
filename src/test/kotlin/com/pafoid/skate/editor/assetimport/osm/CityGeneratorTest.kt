package com.pafoid.skate.editor.assetimport.osm

import com.pafoid.skate.editor.assetstore.AttributionLedger
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.PrefabStore
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.gltf.GlbMesh
import com.pafoid.skate.engine.assets.gltf.GlbWriter
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.MeshCollider3D
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.prefab.PrefabService
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.AssimpCollisionMeshReader
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.utils.AssetsPacker
import com.pafoid.skate.engine.utils.ExportOptions
import com.pafoid.skate.engine.utils.ExportTarget
import com.pafoid.skate.engine.utils.ProjectExporter
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant

/** AST-34 (AST-17 §4.6, §4.7, §7, §9): files written, colliders per backend, regeneration, ledger and credits. */
class CityGeneratorTest {
    @TempDir
    lateinit var dir: File

    private val projectDir get() = File(dir, "Game").also { if (!it.isDirectory) ProjectLayout.create(it) }
    private val paths get() = AssetPaths().apply { projectRoot = projectDir.absoluteFile }
    private val serializer get() = Serializer(paths)
    private val request = CityRequest(OsmDataTest.BOX, "Test Square")

    private fun generator() = CityGenerator(projectDir, serializer) { Instant.parse("2026-11-17T10:00:00Z") }
    private fun generate(backend: PhysicsBackend = PhysicsBackend.BULLET) = generator().generate(request, OsmDataTest.fixture(), backend)

    private fun GameObject.descendants(): List<GameObject> = children.flatMap { listOf(it) + it.descendants() }

    @Test
    fun GlbWriter_OutputLoadsBackThroughAssimpWithTheSameCounts() {
        val mesh = GlbMesh(
            "quad",
            positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 1f),
            normals = FloatArray(12) { if (it % 3 == 1) 1f else 0f },
            uvs = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f),
            indices = intArrayOf(0, 2, 1, 0, 3, 2),
        )
        val file = File(dir, "quad.glb")
        GlbWriter.write(file, "quad", listOf(mesh, GlbMesh("empty", FloatArray(0), FloatArray(0), FloatArray(0), IntArray(0))))
        val bytes = file.readBytes()
        assertEquals("glTF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(bytes.size, java.nio.ByteBuffer.wrap(bytes, 8, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int)
        val loaded = AssimpCollisionMeshReader.read(file.absolutePath)
        assertEquals(4, loaded.vertices.size)
        assertEquals(6, loaded.indices.size)
    }

    @Test
    fun Generate_WritesTheProjectLayoutWithGuidsAndAPlayablePrefab() {
        val result = generate()
        val slug = "test_square"
        assertEquals(slug, result.slug)
        assertTrue(File(projectDir, "Assets/Data/$slug.osm.json").isFile)
        assertTrue(File(projectDir, "Assets/Prefabs/$slug.prefab").isFile)
        val models = File(projectDir, "Assets/Models/$slug").listFiles()!!.filterNot(AssetMeta::isSidecar)
        assertEquals(4, models.count { it.name.endsWith(".collision.glb") }, "one collision model per chunk")
        assertTrue(models.any { it.name == "chunk_0_0_roof.glb" } && models.any { it.name == "chunk_0_0_ground.glb" })
        for (m in CityMaterial.entries.filter { it != CityMaterial.PLAZA }) {
            assertTrue(File(projectDir, "Assets/Materials/${slug}_${m.id}.skmat").isFile, m.id)
        }
        result.files.forEach { assertNotNull(AssetMeta.read(it), "${it.name} has a GUID") }

        // The prefab reloads from disk and instantiates: render parts, and one static mesh collider per chunk by reference.
        val asset = PrefabStore(serializer).load(result.prefabFile)
        val city = PrefabService(serializer).instantiate(asset)
        assertEquals("City Test Square", city.name)
        val chunks = city.children
        assertEquals(4, chunks.size)
        for (chunk in chunks) {
            val collider = chunk.getComponent<MeshCollider3D>()!!
            assertTrue(collider.vertices.isEmpty(), "no inline triangles in the prefab")
            val target = models.single { AssetMeta.read(it)?.guid?.value == collider.meshRef }
            assertTrue(target.name.endsWith(".collision.glb"))
            assertEquals(BodyType.Static, chunk.getComponent<RigidBody3D>()!!.bodyType)
            assertTrue(AssimpCollisionMeshReader.read(target.absolutePath).indices.isNotEmpty(), "collision model loads")
            val parts = chunk.children.mapNotNull { it.getComponent<RenderComponent>() }
            assertTrue(parts.isNotEmpty())
            parts.forEach { r ->
                assertTrue(r.model!!.path.endsWith(".glb") && r.materialPath!!.endsWith(".skmat"))
            }
        }
        // Saved project-relative (loading makes them absolute again), so the project can move.
        val prefabText = result.prefabFile.readText()
        assertTrue("Assets/Models/$slug/chunk_0_0_roof.glb" in prefabText && "Assets/Materials/${slug}_roof.skmat" in prefabText)
        assertFalse(projectDir.absolutePath.replace('\\', '/') in prefabText.replace("\\\\", "/"), "no absolute paths in the prefab")
    }

    @Test
    fun Generate_SimpleBackendUsesOneBoxGameObjectPerBox() {
        val result = generate(PhysicsBackend.SIMPLE)
        assertTrue(File(projectDir, "Assets/Models/${result.slug}").listFiles()!!.none { it.name.endsWith(".collision.glb") })
        for (chunk in result.root.children) {
            assertEquals(null, chunk.getComponent<MeshCollider3D>())
            val boxes = chunk.children.single { it.name == "Colliders" }.children
            assertTrue(boxes.isNotEmpty())
            boxes.forEach { box ->
                assertEquals(1, box.components.count { it is BoxCollider3D }, "one box per GameObject")
                assertEquals(BodyType.Static, box.getComponent<RigidBody3D>()!!.bodyType)
            }
        }
    }

    @Test
    fun Regenerate_AfterABackendChangeIsOfflineAndKeepsGuidsAndStyling() {
        val first = generate(PhysicsBackend.BULLET)
        val roof = File(projectDir, "Assets/Models/${first.slug}/chunk_0_0_roof.glb")
        val roofGuid = AssetMeta.read(roof)!!.guid
        val wall = File(projectDir, "Assets/Materials/${first.slug}_wall.skmat")
        wall.writeText(wall.readText().replace("concrete_simple", "metal")) // a user's restyle

        val second = generator().regenerate(first.snapshotFile, CityOptions(), PhysicsBackend.SIMPLE)
        assertEquals(first.slug, second.slug)
        assertEquals(roofGuid, AssetMeta.read(roof)!!.guid, "render models keep their GUIDs")
        assertTrue(File(projectDir, "Assets/Models/${first.slug}").listFiles()!!.none { it.name.contains(".collision.glb") },
            "collision models (and their .meta) removed after the switch to Simple")
        assertTrue("metal" in wall.readText(), ".skmat edits survive regeneration")
        val ledger = AttributionLedger.load(projectDir)
        assertEquals(1, ledger.entries.size, "same import, same ledger entry")
    }

    @Test
    fun Ledger_RecordsTheOdblAttributionForEveryGeneratedFile() {
        val result = generate()
        val entry = AttributionLedger.load(projectDir).find(CityGenerator.PROVIDER, result.ledgerEntry.sourceId)!!
        assertEquals("ODbL-1.0", entry.license.spdx)
        assertEquals("https://opendatacommons.org/licenses/odbl/1-0/", entry.license.url)
        assertEquals("OpenStreetMap contributors", entry.authors.single().name)
        assertEquals("Test Square", entry.title)
        assertTrue(entry.sourceId.endsWith("@2026-11-10T12:00:00Z"), entry.sourceId)
        assertEquals("2026-11-17T10:00:00Z", entry.retrievedAt)
        assertEquals(result.files.map { AssetMeta.read(it)!!.guid.value }.toSet(), entry.assetIds.toSet())
    }

    private fun project(): Project {
        val scenes = ProjectLayout.scenesDir(projectDir).also { it.mkdirs() }
        val scene = File(scenes, "Main.scene").also { it.writeText("{}") }
        val file = File(projectDir, "Game.skateproject")
        val p = Project(name = "Game", projectPath = file.absolutePath, defaultScene = scene.absolutePath, scenesPath = listOf(scene.absolutePath))
        file.writeText(Serializer().encodeFile(FileKind.PROJECT, p))
        return p
    }

    @Test
    fun Export_NeverPacksTheSnapshotAndCreditsOpenStreetMap() {
        val p = project()
        val city = generate()
        val packer = AssetsPacker(LoggerService())
        assertTrue(packer.selectFiles(p, onlyReferenced = false).none { it.name.endsWith(".osm.json") }, "not even with every file")

        val builds = File(dir, "out")
        val result = ProjectExporter(mockk(relaxed = true)).export(p, ExportOptions(target = ExportTarget.PACK_ONLY, onlyReferenced = false, outputDir = builds.path))
        assertTrue(result.success, "$result")
        val manifest = File(builds, AssetsPacker.MANIFEST_FILE).readText()
        assertFalse(".osm.json" in manifest)
        assertTrue("chunk_0_0_roof.glb" in manifest)
        val credits = File(builds, ProjectExporter.CREDITS_FILE).readText()
        assertTrue("Map data © OpenStreetMap contributors, ODbL 1.0" in credits, credits)
        assertTrue("Test Square" in credits && city.ledgerEntry.sourceUrl in credits)
    }

    @Test
    fun Export_WithoutCreditedFilesWritesNoCredits() {
        val p = project()
        val builds = File(dir, "out")
        File(builds, ProjectExporter.CREDITS_FILE).apply { parentFile.mkdirs(); writeText("stale") }
        assertTrue(ProjectExporter(mockk(relaxed = true)).export(p, ExportOptions(target = ExportTarget.PACK_ONLY, outputDir = builds.path)).success)
        assertFalse(File(builds, ProjectExporter.CREDITS_FILE).exists())
    }
}
