package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.material.MaterialSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.time.Instant

open class StoreImporterTest {
    @TempDir
    lateinit var tmp: File
    lateinit var project: File

    /** The project dir the importer gets; [StoreImporterNonCanonicalRootTest] spells it non-canonically. */
    protected open fun projectRoot(tmp: File): File = tmp

    private lateinit var registry: AssetRegistry
    private lateinit var importer: StoreImporter
    private val folder = "Assets/Store/polyhaven/woodentable_01"

    @BeforeEach
    fun setUp() {
        project = projectRoot(tmp)
        registry = AssetRegistry(project)
        importer = StoreImporter(project, registry) { Instant.parse("2026-11-06T12:00:00Z") }
    }

    private fun stage(variant: String, files: Map<String, String>, sourceId: String = "WoodenTable_01"): File {
        val dir = StorePaths.stagingDir(project, "polyhaven", sourceId, variant)
        files.forEach { (path, text) -> File(dir, path).apply { parentFile.mkdirs(); writeText(text) } }
        return dir
    }

    private fun request(
        files: Map<String, String> = mapOf("a.gltf" to "gltf", "a.bin" to "bin", "textures/t.jpg" to "jpg"),
        variant: String = "gltf-1k",
        details: StoreAssetDetails = cc0Details(),
        material: MaterialSpec? = null,
        modifications: List<String> = emptyList(),
    ): StoreImportRequest {
        val staging = stage(variant, files, details.summary.ref.sourceId)
        val plan = ImportPlan(files.keys.map { ImportEntry(it, it) }, material)
        return StoreImportRequest(details, StoreVariant(variant, SourceFormat.GLTF, "1k", null), staging, plan, modifications)
    }

    @Test
    fun Import_CopiesIntoAssetsStoreWithSidecarsAndLedger() {
        val outcome = importer.import(request())
        assertEquals(File(project, folder).canonicalFile, outcome.targetDir.canonicalFile)
        listOf("a.gltf", "a.bin", "textures/t.jpg").forEach { rel ->
            val file = File(project, "$folder/$rel")
            assertTrue(file.isFile, rel)
            val meta = AssetMeta.read(file)!!
            assertEquals("polyhaven", meta.settings[StoreImporter.SETTING_PROVIDER])
            assertEquals("WoodenTable_01", meta.settings[StoreImporter.SETTING_SOURCE_ID])
            assertEquals("CC0-1.0", meta.settings[StoreImporter.SETTING_LICENSE])
            assertEquals(meta.guid, registry.idOf("$folder/$rel"))
        }
        val entry = AttributionLedger.load(project).find("polyhaven", "WoodenTable_01")!!
        assertEquals(folder, entry.folder)
        assertEquals("gltf-1k", entry.variant)
        assertEquals("CC0-1.0", entry.license.spdx)
        assertEquals(listOf("Ethan Place"), entry.authors.map { it.name })
        assertEquals("2026-11-06T12:00:00Z", entry.retrievedAt)
        assertEquals(outcome.files.map { AssetMeta.read(it)!!.guid.value }, entry.assetIds)
    }

    @Test
    fun Revert_DeletesFilesSidecarsFolderAndLedgerEntry() {
        val outcome = importer.import(request())
        importer.revert(outcome, outcome.let { cc0Details().summary.ref })
        assertFalse(File(project, folder).exists())
        assertNull(registry.idOf("$folder/a.gltf"))
        assertTrue(AttributionLedger.load(project).entries.isEmpty())
        assertTrue(File(project, "Assets/Store").walkTopDown().none { it.isFile })
    }

    @Test
    fun Reimport_KeepsGuidsRemovesDroppedFilesAndRevertRestores() {
        importer.import(request())
        val gltf = File(project, "$folder/a.gltf")
        val tex = File(project, "$folder/textures/t.jpg")
        val gltfId = AssetMeta.read(gltf)!!.guid
        val texId = AssetMeta.read(tex)!!.guid

        val second = importer.import(request(files = mapOf("a.gltf" to "gltf v2", "a.bin" to "bin"), variant = "gltf-2k"))
        assertEquals(gltfId, AssetMeta.read(gltf)!!.guid)
        assertEquals("gltf v2", gltf.readText())
        assertFalse(tex.exists())
        assertFalse(AssetMeta.sidecarOf(tex).exists())
        val entry = AttributionLedger.load(project).find("polyhaven", "WoodenTable_01")!!
        assertEquals("gltf-2k", entry.variant)
        assertFalse(texId.value in entry.assetIds)

        importer.revert(second, cc0Details().summary.ref)
        assertEquals("gltf", gltf.readText())
        assertEquals(gltfId, AssetMeta.read(gltf)!!.guid)
        assertEquals(texId, AssetMeta.read(tex)!!.guid)
        assertEquals(texId, registry.idOf("$folder/textures/t.jpg"))
        assertEquals("gltf-1k", AttributionLedger.load(project).find("polyhaven", "WoodenTable_01")!!.variant)
    }

    @Test
    fun Import_TextureSetWritesMaterialWithProjectRelativeTextures() {
        val details = cc0Details("rock_wall_08", StoreAssetKind.TEXTURE_SET)
        val files = mapOf("rock_wall_08_diff_1k.jpg" to "d", "rock_wall_08_nor_gl_1k.jpg" to "n", "rock_wall_08_arm_1k.jpg" to "a")
        val material = MaterialSpec("rock_wall_08", mapOf(
            MaterialSchema.Companion.Tex.BASE_COLOR to "rock_wall_08_diff_1k.jpg",
            MaterialSchema.Companion.Tex.NORMAL to "rock_wall_08_nor_gl_1k.jpg",
            MaterialSchema.Companion.Tex.METALLIC_ROUGHNESS to "rock_wall_08_arm_1k.jpg",
        ))
        val outcome = importer.import(request(files, "jpg-1k", details, material))
        val skmat = File(project, "Assets/Store/polyhaven/rock_wall_08/rock_wall_08.skmat")
        assertTrue(skmat.canonicalFile in outcome.files.map { it.canonicalFile })
        val asset = MaterialAsset.load(skmat)
        assertEquals("Assets/Store/polyhaven/rock_wall_08/rock_wall_08_diff_1k.jpg", asset.textures[MaterialSchema.Companion.Tex.BASE_COLOR])
        assertNotNull(registry.idOf("Assets/Store/polyhaven/rock_wall_08/rock_wall_08.skmat"))
        assertEquals(4, AttributionLedger.load(project).entries.single().assetIds.size)
    }

    @Test
    fun Import_UnknownLicenseIsBlocked() {
        val details = cc0Details(license = LicenseCatalog.UNKNOWN)
        assertThrows<IOException> { importer.import(request(details = details)) }
        assertFalse(File(project, "Assets").exists())
        assertFalse(AttributionLedger.file(project).exists())
    }

    @Test
    fun Import_FbxIsNotImportableInV1() {
        assertThrows<IOException> { importer.import(request(files = mapOf("a.fbx" to "fbx"))) }
        assertFalse(File(project, folder).exists())
    }

    @Test
    fun Import_UnsafeTargetPathIsRejected() {
        val req = request()
        val bad = req.copy(plan = ImportPlan(listOf(ImportEntry("a.gltf", "../../escape.gltf"))))
        assertThrows<IOException> { importer.import(bad) }
        assertFalse(File(project, "Assets/escape.gltf").exists())
    }

    @Test
    fun Import_DifferentSourceWithSameSlugGetsSuffix() {
        File(project, "$folder/other.png").apply { parentFile.mkdirs(); writeText("x") }
        val outcome = importer.import(request())
        assertEquals(File(project, "${folder}_2").canonicalFile, outcome.targetDir.canonicalFile)
    }

    @Test
    fun Import_NoDerivativesLicense_RecordsConversion() {
        val details = cc0Details(license = LicenseCatalog.CC_BY_ND_4)
        importer.import(request(details = details, modifications = listOf("converted USD -> glTF")))
        val mods = AttributionLedger.load(project).entries.single().modifications
        assertEquals(listOf("converted USD -> glTF", StoreImporter.ND_NOTE), mods)
    }

    @Test
    fun Reimport_LedgerFolderOutsideStore_IsNotTrusted() {
        val outside = File(project, "Scenes/keep.png").apply { parentFile.mkdirs(); writeText("keep") }
        val evil = File(project.parentFile, "evil-${project.name}/x.png").apply { parentFile.mkdirs(); writeText("x") }
        listOf("../evil-${project.name}", "Scenes", "Assets", "Assets/Store/../../Scenes").forEach { folder ->
            AttributionLedger.save(project, AttributionLedger().upsert(LedgerEntry(listOf("1"), "polyhaven", "WoodenTable_01",
                title = "t", sourceUrl = "u", license = LedgerLicense("CC0-1.0"), retrievedAt = "r", folder = folder)))
            val outcome = importer.import(request())
            assertEquals(File(project, folder("woodentable_01")).canonicalFile, outcome.targetDir.canonicalFile, folder)
            importer.revert(outcome, cc0Details().summary.ref)
        }
        assertTrue(outside.isFile)
        assertTrue(evil.isFile)
        evil.parentFile.deleteRecursively()
    }

    private fun folder(slug: String) = "Assets/Store/polyhaven/$slug"

    @Test
    fun Import_StagingOutsideProject_IsRejected() {
        val req = request()
        val foreign = File(project.parentFile, "other-${project.name}/.skate/cache/store/polyhaven/x/gltf-1k").apply { mkdirs() }
        assertThrows<IOException> { importer.import(req.copy(stagingDir = foreign)) }
        assertFalse(File(project, folder).exists())
        foreign.parentFile.parentFile.parentFile.parentFile.parentFile.parentFile.deleteRecursively()
    }

    @Test
    fun Import_WritesGitignoreForCache() {
        importer.import(request())
        assertEquals("cache/\n", File(project, ".skate/.gitignore").readText())
    }

    @Test
    fun Reimport_KeepsOnlyRecentBackupsAndPrunesAllOnClose() {
        var now = Instant.parse("2026-11-06T12:00:00Z")
        val clocked = StoreImporter(project, registry) { now.also { now = now.plusSeconds(1) } }
        repeat(8) { clocked.import(request(files = mapOf("a.gltf" to "v$it"))) }
        val itemDir = File(project, ".skate/cache/store/polyhaven/woodentable_01")
        val backups = itemDir.listFiles { f -> f.name.startsWith(StoreImporter.BACKUP_PREFIX) }!!
        assertEquals(StoreImporter.KEEP_BACKUPS, backups.size)
        StoreImporter.pruneAllBackups(project)
        assertTrue(itemDir.listFiles { f -> f.name.startsWith(StoreImporter.BACKUP_PREFIX) }!!.isEmpty())
        assertTrue(File(itemDir, "gltf-1k/a.gltf").isFile, "staging is kept for redo")
    }

    @Test
    fun RevertReimport_RestoresOverwrittenSidecars() {
        importer.import(request())
        val gltf = File(project, "$folder/a.gltf")
        val second = importer.import(request(details = cc0Details(license = LicenseCatalog.CC_BY_4)))
        assertEquals("CC-BY-4.0", AssetMeta.read(gltf)!!.settings[StoreImporter.SETTING_LICENSE])
        importer.revert(second, cc0Details().summary.ref)
        assertEquals("CC0-1.0", AssetMeta.read(gltf)!!.settings[StoreImporter.SETTING_LICENSE])
    }

    @Test
    fun Stage_DoesNotRegisterOrWriteLedger_AndDiscardCleansUp() {
        val staged = importer.stage(request())
        assertTrue(File(project, "$folder/a.gltf").isFile)
        assertNull(registry.idOf("$folder/a.gltf"))
        assertFalse(AttributionLedger.file(project).exists())
        importer.discard(staged)
        assertFalse(File(project, folder).exists())
    }
}

/** Every test again with the project dir spelled non-canonically: CI's 8.3 temp path, reproduced locally. */
class StoreImporterNonCanonicalRootTest : StoreImporterTest() {
    override fun projectRoot(tmp: File): File = nonCanonicalProjectRoot(tmp).also { assertNotEquals(it.absoluteFile, it.canonicalFile) }
}
