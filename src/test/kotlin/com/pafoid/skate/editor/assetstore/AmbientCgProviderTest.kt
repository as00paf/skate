package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.editor.assetstore.providers.ambientcg.AmbientCgProvider
import com.pafoid.skate.editor.events.AssetStoreEvent
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.material.MaterialSchema
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

class AmbientCgProviderTest {
    @TempDir
    lateinit var tmp: File

    private val base = AmbientCgProvider.API_BASE
    private fun fixture(name: String) = AmbientCgProviderTest::class.java.getResource("/assetstore/ambientcg/$name")!!.readText()
    private val detailsUrl = "$base/assets?id=Bricks059&include=${AmbientCgProvider.DETAILS_INCLUDE}"

    /** ambientCG API v3 responses recorded on 2026-11-06. */
    private val http = FakeStoreHttp()
        .route("$base/assets?type=material&sort=popular&limit=3&offset=0&include=${AmbientCgProvider.SEARCH_INCLUDE}", fixture("list_materials.json"))
        .route("$base/assets?type=material&sort=popular&limit=3&offset=0&include=${AmbientCgProvider.SEARCH_INCLUDE}&q=brick", fixture("search_brick.json"))
        .route(detailsUrl, fixture("details_Bricks059.json"))
    private val provider = AmbientCgProvider(http)

    @Test
    fun AmbientCg_ListAndSearch_MapTitlesThumbnailsAndPaging() {
        val list = provider.search(StoreQuery(kind = StoreAssetKind.TEXTURE_SET, pageSize = 3))
        assertEquals(listOf("PavingStones152", "Concrete049", "Fabric086"), list.items.map { it.ref.sourceId })
        assertEquals("Paving Stones 152", list.items.first().title)
        assertEquals(2040, list.total)
        assertEquals("3", list.nextCursor)
        assertTrue(list.items.first().thumbnailUrl!!.endsWith("/256-PNG/PavingStones152.png"))
        assertEquals(LicenseCatalog.CC0, list.items.first().license)

        val search = provider.search(StoreQuery(text = "brick", kind = StoreAssetKind.TEXTURE_SET, pageSize = 3))
        assertEquals(listOf("Ground111", "Ground107", "Bricks097"), search.items.map { it.ref.sourceId })
        assertEquals(31, search.total)
    }

    @Test
    fun AmbientCg_Details_OffersZipVariantsJpgFirst() {
        val details = provider.details(StoreAssetRef("ambientcg", "Bricks059"))
        assertEquals("Bricks 059", details.summary.title)
        assertEquals("https://ambientcg.com/view?id=Bricks059", details.sourceUrl)
        assertEquals(listOf("1K-JPG", "1K-PNG", "2K-JPG", "2K-PNG"), details.variants.take(4).map { it.id })
        assertTrue(details.variants.all { it.format == SourceFormat.TEXTURE_SET && (it.sizeBytes ?: 0) > 0 })
        assertEquals(listOf("ambientCG"), details.authors.map { it.name })
    }

    @Test
    fun AmbientCg_ResolveDownload_IsOneZipPerVariant() {
        val details = provider.details(StoreAssetRef("ambientcg", "Bricks059"))
        val plan = provider.resolveDownload(details, details.variants.first())
        val item = plan.items.single()
        assertEquals("Bricks059_1K-JPG.zip", item.relativePath)
        assertEquals("https://ambientcg.com/get?file=Bricks059_1K-JPG.zip", item.url)
        assertEquals(details.variants.first().sizeBytes, item.sizeBytes)
    }

    @Test
    fun AmbientCg_FailsSoft_OnBadAssetsAndBadResponses() {
        val soft = FakeStoreHttp()
            .route("$base/assets?type=material&sort=popular&limit=3&offset=0&include=${AmbientCgProvider.SEARCH_INCLUDE}",
                """{"totalResults":2,"nextPageHttp":null,"assets":[{"title":"no id"},{"id":"Ok001","thumbnails":[]},"junk"]}""")
            .route("$base/assets?type=material&sort=popular&limit=3&offset=0&include=${AmbientCgProvider.SEARCH_INCLUDE}&q=x", "<html>maintenance</html>")
        val p = AmbientCgProvider(soft)
        val page = p.search(StoreQuery(kind = StoreAssetKind.TEXTURE_SET, pageSize = 3))
        assertEquals(listOf("Ok001"), page.items.map { it.ref.sourceId })
        assertNull(page.items.single().thumbnailUrl)
        assertNull(page.nextCursor)
        assertThrows<StoreHttpException> { p.search(StoreQuery(text = "x", pageSize = 3)) }
        assertThrows<StoreHttpException> { p.details(StoreAssetRef("ambientcg", "Missing001")) }
        assertThrows<IllegalArgumentException> { p.details(StoreAssetRef("ambientcg", "../x")) }
    }

    @Test
    fun AmbientCg_PostProcess_UnzipsAndPacksOrm() {
        val staging = File(tmp, "staging").apply { mkdirs() }
        File(staging, "Bricks059_1K-JPG.zip").writeBytes(materialZip(metal = true))
        val details = cc0Details("Bricks059", StoreAssetKind.TEXTURE_SET)
        val variant = StoreVariant("1K-JPG", SourceFormat.TEXTURE_SET, "1K", null)
        val files = provider.postProcess(details, variant, staging, listOf("Bricks059_1K-JPG.zip"))
        assertTrue("Bricks059_1K-JPG_ORM.png" in files)
        val orm = ImageIO.read(File(staging, "Bricks059_1K-JPG_ORM.png"))
        val px = orm.getRGB(0, 0)
        assertEquals(AO, (px shr 16) and 0xFF, "R = AO")
        assertEquals(ROUGH, (px shr 8) and 0xFF, "G = roughness")
        assertEquals(METAL, px and 0xFF, "B = metalness")

        val plan = provider.importPlan(details, variant, files)
        assertEquals(setOf("Bricks059_1K-JPG_Color.png", "Bricks059_1K-JPG_NormalGL.png", "Bricks059_1K-JPG_ORM.png"), plan.entries.map { it.targetPath }.toSet())
        val tex = plan.material!!.textures
        assertEquals("Bricks059_1K-JPG_Color.png", tex[MaterialSchema.Companion.Tex.BASE_COLOR])
        assertEquals("Bricks059_1K-JPG_NormalGL.png", tex[MaterialSchema.Companion.Tex.NORMAL])
        assertEquals("Bricks059_1K-JPG_ORM.png", tex[MaterialSchema.Companion.Tex.METALLIC_ROUGHNESS])
        assertEquals("Bricks059_1K-JPG_ORM.png", tex[MaterialSchema.Companion.Tex.AO])
    }

    @Test
    fun AmbientCg_PostProcess_WithoutMetalOrAo_UsesNeutralValues() {
        val staging = File(tmp, "staging").apply { mkdirs() }
        File(staging, "a.zip").writeBytes(materialZip(metal = false, ao = false))
        val details = cc0Details("Bricks059", StoreAssetKind.TEXTURE_SET)
        val variant = StoreVariant("1K-JPG", SourceFormat.TEXTURE_SET, "1K", null)
        val files = provider.postProcess(details, variant, staging, listOf("a.zip"))
        val px = ImageIO.read(File(staging, "Bricks059_1K-JPG_ORM.png")).getRGB(1, 1)
        assertEquals(255, (px shr 16) and 0xFF)
        assertEquals(0, px and 0xFF)
        assertFalse(MaterialSchema.Companion.Tex.AO in provider.importPlan(details, variant, files).material!!.textures)
    }

    @Test
    fun AmbientCg_EndToEnd_DownloadStageAndImportMaterial() {
        val project = File(tmp, "project").apply { mkdirs() }
        http.route("https://ambientcg.com/get?file=Bricks059_1K-JPG.zip", materialZip(metal = true))
        val events = mutableListOf<com.pafoid.skate.engine.events.Event>()
        val details = provider.details(StoreAssetRef("ambientcg", "Bricks059"))
        // The recorded size is the real zip's; the test zip is smaller, so drop the size check for this variant.
        val variant = details.variants.first()
        val sizeless = object : AssetStoreProvider by provider {
            override fun resolveDownload(details: StoreAssetDetails, variant: StoreVariant) =
                provider.resolveDownload(details, variant).let { p -> p.copy(items = p.items.map { it.copy(sizeBytes = null) }) }
        }
        val svc = AssetStoreService(AssetStoreRegistry(listOf(sizeless)), http, InlineJobs, { events += it }, { project }, File(tmp, "cache"))
        svc.import(details, variant)
        assertNull(svc.downloadError)
        val request = (events.single() as AssetStoreEvent.ImportRequested).request
        StoreImporter(project, AssetRegistry(project)).import(request)
        val dir = File(project, "Assets/Store/ambientcg/bricks059")
        assertEquals(setOf("Bricks059_1K-JPG_Color.png", "Bricks059_1K-JPG_NormalGL.png", "Bricks059_1K-JPG_ORM.png", "bricks059.skmat"),
            dir.listFiles()!!.filter { !it.name.endsWith(".meta") }.map { it.name }.toSet())
        val material = MaterialAsset.load(File(dir, "bricks059.skmat"))
        assertEquals("Assets/Store/ambientcg/bricks059/Bricks059_1K-JPG_ORM.png", material.textures[MaterialSchema.Companion.Tex.METALLIC_ROUGHNESS])
        assertEquals("CC0-1.0", AttributionLedger.load(project).find("ambientcg", "Bricks059")!!.license.spdx)
    }

    private fun materialZip(metal: Boolean, ao: Boolean = true): ByteArray {
        val maps = buildMap {
            put("Color", 0x804020)
            put("NormalGL", 0x8080FF)
            put("NormalDX", 0x8080FF)
            put("Roughness", gray(ROUGH))
            put("Displacement", gray(10))
            if (metal) put("Metalness", gray(METAL))
            if (ao) put("AmbientOcclusion", gray(AO))
        }
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            maps.forEach { (map, rgb) ->
                zip.putNextEntry(ZipEntry("Bricks059_1K-JPG_$map.png"))
                zip.write(png(rgb))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("Bricks059_1K-JPG.usdc"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    private fun png(rgb: Int): ByteArray {
        val img = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 4) for (x in 0 until 4) img.setRGB(x, y, rgb)
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }

    private fun gray(v: Int) = (v shl 16) or (v shl 8) or v

    private companion object {
        const val ROUGH = 200
        const val METAL = 90
        const val AO = 150
    }
}
