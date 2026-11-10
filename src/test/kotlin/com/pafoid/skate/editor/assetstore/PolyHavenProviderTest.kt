package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.editor.assetstore.providers.polyhaven.PolyHavenProvider
import com.pafoid.skate.engine.render.material.MaterialSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PolyHavenProviderTest {
    private val http = PolyHavenFixtures.http()
    private val provider = PolyHavenProvider(http)

    @Test
    fun PolyHaven_BlankSearch_ListsCatalogByDownloadsWithPaging() {
        val first = provider.search(StoreQuery(kind = StoreAssetKind.MODEL, pageSize = 2))
        assertEquals(5, first.total)
        assertEquals(2, first.items.size)
        assertEquals("2", first.nextCursor)
        val all = provider.search(StoreQuery(kind = StoreAssetKind.MODEL, pageSize = 10)).items
        val counts = all.map { it.ref.sourceId }
        assertEquals(counts, counts.sortedByDescending { downloads(it) })
        val last = provider.search(StoreQuery(kind = StoreAssetKind.MODEL, pageSize = 2, cursor = "4"))
        assertEquals(1, last.items.size)
        assertNull(last.nextCursor)
    }

    @Test
    fun PolyHaven_TextSearch_KeepsRankingAndCatalogNames() {
        val page = provider.search(StoreQuery(text = "chair", kind = StoreAssetKind.MODEL))
        assertEquals(listOf("GreenChair_01", "WoodenChair_01", "SchoolChair_01"), page.items.map { it.ref.sourceId })
        val item = page.items.first()
        assertEquals("polyhaven", item.ref.providerId)
        assertEquals(LicenseCatalog.CC0, item.license)
        assertEquals("${PolyHavenProvider.THUMBS}/GreenChair_01.png?width=256&height=256", item.thumbnailUrl)
        assertTrue(item.title.isNotBlank())
    }

    @Test
    fun PolyHaven_Cc0OnlyAndCommercialFilters_KeepItems() {
        assertEquals(5, provider.search(StoreQuery(licenseFilter = LicenseFilter.CC0_ONLY)).total)
        assertEquals(5, provider.search(StoreQuery(licenseFilter = LicenseFilter.COMMERCIAL_OK)).total)
    }

    @Test
    fun PolyHaven_CatalogIsFetchedOncePerKind() {
        provider.search(StoreQuery())
        provider.search(StoreQuery(text = "chair"))
        assertEquals(1, http.gets.count { it.endsWith("/assets?type=models") })
    }

    @Test
    fun PolyHaven_ModelDetails_HaveGltfVariantsSortedBySize() {
        val details = provider.details(StoreAssetRef("polyhaven", "WoodenTable_01"))
        assertEquals("Wooden Table 01", details.summary.title)
        assertEquals(StoreAssetKind.MODEL, details.summary.kind)
        assertEquals(listOf("Ethan Place"), details.authors.map { it.name })
        assertEquals("https://polyhaven.com/a/WoodenTable_01", details.sourceUrl)
        assertEquals(listOf("1k", "2k", "4k"), details.variants.map { it.resolution })
        assertTrue(details.variants.all { it.format == SourceFormat.GLTF && (it.sizeBytes ?: 0) > 0 })
    }

    @Test
    fun PolyHaven_ModelDownload_IncludesBinAndTexturesWithChecksums() {
        val details = provider.details(StoreAssetRef("polyhaven", "WoodenTable_01"))
        val plan = provider.resolveDownload(details, details.variants.first { it.resolution == "1k" })
        val paths = plan.items.map { it.relativePath }
        assertEquals("WoodenTable_01_1k.gltf", paths.first())
        assertTrue("WoodenTable_01.bin" in paths)
        assertTrue(paths.any { it.startsWith("textures/") && it.endsWith(".jpg") })
        assertTrue(plan.items.all { it.md5 != null && it.sizeBytes != null && it.url.startsWith("https://dl.polyhaven.org/") })
        assertTrue(paths.all { StorePaths.safeRelative(it) == it })
    }

    @Test
    fun PolyHaven_TextureSet_DownloadsDiffuseNormalGlAndArmAsJpg() {
        val details = provider.details(StoreAssetRef("polyhaven", "rock_wall_08"))
        assertEquals(StoreAssetKind.TEXTURE_SET, details.summary.kind)
        assertEquals("1k", details.variants.first().resolution)
        val plan = provider.resolveDownload(details, details.variants.first())
        assertEquals(listOf("rock_wall_08_diff_1k.jpg", "rock_wall_08_nor_gl_1k.jpg", "rock_wall_08_arm_1k.jpg"), plan.items.map { it.relativePath })
    }

    @Test
    fun PolyHaven_TextureSetImportPlan_MapsSlotsAndAddsMaterial() {
        val details = provider.details(StoreAssetRef("polyhaven", "rock_wall_08"))
        val staged = listOf("rock_wall_08_diff_1k.jpg", "rock_wall_08_nor_gl_1k.jpg", "rock_wall_08_arm_1k.jpg")
        val plan = provider.importPlan(details, details.variants.first(), staged)
        assertEquals(staged, plan.entries.map { it.targetPath })
        val tex = plan.material!!.textures
        assertEquals("rock_wall_08_diff_1k.jpg", tex[MaterialSchema.Companion.Tex.BASE_COLOR])
        assertEquals("rock_wall_08_nor_gl_1k.jpg", tex[MaterialSchema.Companion.Tex.NORMAL])
        assertEquals("rock_wall_08_arm_1k.jpg", tex[MaterialSchema.Companion.Tex.METALLIC_ROUGHNESS])
        assertEquals("rock_wall_08_arm_1k.jpg", tex[MaterialSchema.Companion.Tex.AO])
    }

    @Test
    fun PolyHaven_ModelImportPlan_HasNoMaterial() {
        val details = provider.details(StoreAssetRef("polyhaven", "WoodenTable_01"))
        val plan = provider.importPlan(details, details.variants.first(), listOf("a.gltf", "a.bin"))
        assertNull(plan.material)
        assertEquals(2, plan.entries.size)
    }

    @Test
    fun PolyHaven_HttpError_Throws() {
        val e = assertThrows<StoreHttpException> { provider.details(StoreAssetRef("polyhaven", "missing")) }
        assertEquals(404, e.status)
    }

    private fun downloads(slug: String): Long =
        Json.parseToJsonElement(PolyHavenFixtures::class.java.getResource("/assetstore/polyhaven/assets_models.json")!!.readText())
            .jsonObject.getValue(slug).jsonObject.getValue("download_count").jsonPrimitive.long
}
