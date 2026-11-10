package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.editor.assetstore.providers.catalog.CatalogPackProvider
import com.pafoid.skate.editor.events.AssetStoreEvent
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.events.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CatalogPackProviderTest {
    @TempDir
    lateinit var tmp: File

    private val kenney = CatalogPackProvider.load("/assetstore/kenney.catalog.json", "assetstore.provider.kenney", "assetstore.provider.kenney.powered_by")
    private val quaternius = CatalogPackProvider.load("/assetstore/quaternius.catalog.json", "assetstore.provider.quaternius", null)

    @Test
    fun Catalogs_ShippedCatalogsAreValidCc0PagesOnTheCreatorsSite() {
        listOf(kenney to "https://kenney.nl/assets/", quaternius to "https://quaternius.com/packs/").forEach { (p, prefix) ->
            assertTrue(p.manualDownload)
            assertTrue(p.catalog.packs.size >= 10, p.id)
            assertTrue(p.catalog.packs.all { it.pageUrl.startsWith(prefix) }, p.id)
            assertEquals(LicenseCatalog.CC0, p.details(StoreAssetRef(p.id, p.catalog.packs.first().id)).license)
        }
        assertEquals(listOf("kenney", "quaternius"), listOf(kenney.id, quaternius.id))
        assertNotNull(kenney.catalog.packs.firstOrNull { it.id == "mini-skate" })
    }

    @Test
    fun Catalogs_SearchByWordsAndTagsWithPaging() {
        assertEquals(listOf("mini-skate"), kenney.search(StoreQuery(text = "skate")).items.map { it.ref.sourceId })
        val city = kenney.search(StoreQuery(text = "city", pageSize = 2))
        assertEquals(3, city.total)
        assertEquals("2", city.nextCursor)
        assertEquals(listOf("city-kit-roads"), kenney.search(StoreQuery(text = "city roads")).items.map { it.ref.sourceId })
        assertEquals(0, kenney.search(StoreQuery(kind = StoreAssetKind.TEXTURE_SET)).total)
        assertNull(kenney.search(StoreQuery(text = "car")).items.first().thumbnailUrl)
    }

    @Test
    fun Catalogs_NoDownloadApi() {
        val details = kenney.details(StoreAssetRef("kenney", "mini-skate"))
        assertEquals("https://kenney.nl/assets/mini-skate", details.sourceUrl)
        assertEquals(SourceFormat.ZIP_PACK, details.variants.single().format)
        assertThrows<UnsupportedOperationException> { kenney.resolveDownload(details, details.variants.single()) }
    }

    @Test
    fun Catalogs_MatchZipNamesLongestFirst() {
        assertEquals("city-kit-roads", kenney.matchPack("kenney_city-kit-roads_1.2.zip")?.sourceId)
        assertEquals("toy-car-kit", kenney.matchPack("C:\\Downloads\\kenney_toy-car-kit.zip")?.sourceId)
        assertEquals("car-kit", kenney.matchPack("kenney_car-kit (1).zip")?.sourceId)
        assertEquals("mini-skate", kenney.matchPack("Mini Skate.zip")?.sourceId)
        assertEquals("downtowncitymegakit", quaternius.matchPack("Downtown City MegaKit[Standard].zip")?.sourceId)
        assertEquals("simplebuildings", quaternius.matchPack("Simple Buildings Pack.zip")?.sourceId)
        assertNull(kenney.matchPack("holiday.zip"))
        assertEquals("citykitroads", CatalogPackProvider.normalize("kenney_city-kit-roads_2.0").removePrefix("kenney"))
    }

    @Test
    fun ImportPlan_PrefersGltfWithCompanionsAndStripsCommonRoot() {
        val details = kenney.details(StoreAssetRef("kenney", "mini-skate"))
        val staged = listOf(
            "Models/GLB format/ramp.glb", "Models/GLB format/Textures/colormap.png",
            "Models/OBJ format/ramp.obj", "Models/OBJ format/ramp.mtl", "Preview.png",
        )
        val plan = kenney.importPlan(details, details.variants.single(), staged)
        assertEquals(listOf(ImportEntry("Models/GLB format/ramp.glb", "ramp.glb"), ImportEntry("Models/GLB format/Textures/colormap.png", "Textures/colormap.png")), plan.entries)
        assertNull(plan.material)
    }

    @Test
    fun ImportPlan_FallsBackToObjAndRejectsFbxOnlyPacks() {
        val details = quaternius.details(StoreAssetRef("quaternius", "cars"))
        val obj = quaternius.importPlan(details, details.variants.single(), listOf("Cars/OBJ/Car.obj", "Cars/OBJ/Car.mtl", "Cars/OBJ/Car.png", "Cars/Blends/x.png"))
        assertEquals(listOf("Car.obj", "Car.mtl", "Car.png"), obj.entries.map { it.targetPath })
        assertTrue(quaternius.importPlan(details, details.variants.single(), listOf("Cars/FBX/Car.png")).entries.isEmpty())
    }

    // --- "Import downloaded pack…" end to end, no network ---

    private val events = mutableListOf<Event>()
    private val project by lazy { File(tmp, "project").apply { mkdirs() } }

    private fun service() = AssetStoreService(AssetStoreRegistry(listOf(kenney, quaternius)), FakeStoreHttp(), InlineJobs, { events += it }, { project }, File(tmp, "cache"))

    private fun zip(name: String, vararg entries: String): File {
        val f = File(tmp, name)
        ZipOutputStream(f.outputStream()).use { z -> entries.forEach { z.putNextEntry(ZipEntry(it)); z.write(it.toByteArray()); z.closeEntry() } }
        return f
    }

    @Test
    fun ImportPack_SelectedPack_StagesModelsAndImportsWithAttribution() {
        val zip = zip("kenney_mini-skate_1.0.zip", "Models/GLB format/ramp.glb", "Models/GLB format/Textures/colormap.png", "Models/FBX format/ramp.fbx", "License.txt")
        val s = service()
        s.importPack(kenney.details(StoreAssetRef("kenney", "mini-skate")), zip)
        val request = (events.single() as AssetStoreEvent.ImportRequested).request
        assertTrue(File(request.stagingDir, "Models/GLB format/ramp.glb").isFile)
        assertTrue(!File(request.stagingDir, "Models/FBX format/ramp.fbx").exists(), "only importable entries are extracted")
        StoreImporter(project, AssetRegistry(project)).import(request)
        val dir = File(project, "Assets/Store/kenney/mini-skate")
        assertTrue(File(dir, "ramp.glb").isFile)
        assertTrue(File(dir, "Textures/colormap.png").isFile)
        val entry = AttributionLedger.load(project).find("kenney", "mini-skate")!!
        assertEquals("https://kenney.nl/assets/mini-skate", entry.sourceUrl)
        assertEquals("Kenney", entry.authors.single().name)
        assertEquals("CC0-1.0", entry.license.spdx)
    }

    @Test
    fun ImportPack_RecognisedFromFileName() {
        val s = service()
        s.selectProvider("quaternius")
        s.importPack(null, zip("Downtown City MegaKit[Standard].zip", "glTF/Building.gltf", "glTF/Building.bin"))
        val request = (events.single() as AssetStoreEvent.ImportRequested).request
        assertEquals(StoreAssetRef("quaternius", "downtowncitymegakit"), request.ref)
        assertEquals(listOf("Building.gltf", "Building.bin"), request.plan.entries.map { it.targetPath })
    }

    @Test
    fun ImportPack_RefusesWrongUnknownOrUnusableZips() {
        val s = service()
        s.importPack(kenney.details(StoreAssetRef("kenney", "mini-skate")), zip("kenney_car-kit.zip", "a.glb"))
        assertTrue(s.downloadError!!.contains("car-kit"))
        s.importPack(null, zip("whatever.zip", "a.glb"))
        assertTrue(s.downloadError!!.contains("select the pack"))
        s.importPack(kenney.details(StoreAssetRef("kenney", "mini-skate")), zip("mini-skate.zip", "Models/FBX format/ramp.fbx"))
        assertTrue(s.downloadError!!.contains("no glTF"))
        s.importPack(kenney.details(StoreAssetRef("kenney", "mini-skate")), zip("mini-skate2.zip", "../../evil.glb"))
        assertNotNull(s.downloadError)
        assertTrue(!File(tmp, "evil.glb").exists())
        assertTrue(events.isEmpty())
    }
}
