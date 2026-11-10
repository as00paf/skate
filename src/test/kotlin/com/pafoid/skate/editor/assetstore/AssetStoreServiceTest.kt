package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.editor.events.AssetStoreEvent
import com.pafoid.skate.engine.events.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AssetStoreServiceTest {
    @TempDir
    lateinit var tmp: File

    private val http = FakeStoreHttp().route("https://x/a.gltf", "gltf").route("https://x/thumb.png", "png")
    private val events = mutableListOf<Event>()

    private class FakeProvider : AssetStoreProvider {
        override val id = "fake"
        override val displayNameKey = "assetstore.provider.polyhaven"
        override val kinds = setOf(StoreAssetKind.MODEL)
        override val poweredByKey: String? = null
        var queries = mutableListOf<StoreQuery>()

        override fun search(query: StoreQuery): StorePage {
            queries += query
            val start = query.cursor?.toInt() ?: 0
            val items = (start until start + 2).map { StoreAssetSummary(StoreAssetRef(id, "${query.text}$it"), "T$it", StoreAssetKind.MODEL, "https://x/thumb.png", LicenseCatalog.CC0) }
            return StorePage(items, if (start == 0) "2" else null, 4)
        }

        override fun details(ref: StoreAssetRef) = cc0Details(ref.sourceId).let { it.copy(summary = it.summary.copy(ref = ref)) }
        override fun resolveDownload(details: StoreAssetDetails, variant: StoreVariant) = DownloadPlan(listOf(DownloadItem("https://x/a.gltf", "a.gltf")))
        override fun importPlan(details: StoreAssetDetails, variant: StoreVariant, staged: List<String>) = ImportPlan(staged.map { ImportEntry(it, it) })
    }

    private val provider = FakeProvider()
    private var projectDir: File? = null

    private fun service(jobs: StoreJobs = InlineJobs) =
        AssetStoreService(AssetStoreRegistry(listOf(provider)), http, jobs, { events += it }, { projectDir }, File(tmp, "cache"))

    @Test
    fun Service_SearchAndLoadMore() {
        val s = service()
        s.search(StoreQuery(text = "q"))
        assertEquals(listOf("q0", "q1"), s.results.map { it.ref.sourceId })
        assertEquals(4, s.total)
        s.loadMore()
        assertEquals(listOf("q0", "q1", "q2", "q3"), s.results.map { it.ref.sourceId })
        assertNull(s.nextCursor)
    }

    @Test
    fun Service_StaleSearchResultIsIgnored() {
        val queue = ArrayDeque<() -> Unit>()
        val jobs = object : StoreJobs {
            override fun io(block: () -> Unit) { queue += block }
            override fun main(block: () -> Unit) = block()
        }
        val s = service(jobs)
        s.search(StoreQuery(text = "old"))
        s.search(StoreQuery(text = "new"))
        queue.removeLast()()
        queue.removeLast()()
        assertEquals(listOf("new0", "new1"), s.results.map { it.ref.sourceId })
    }

    @Test
    fun Service_SelectLoadsDetails() {
        val s = service()
        s.search(StoreQuery(text = "q"))
        s.select(s.results.first())
        assertEquals("q0", s.details!!.summary.ref.sourceId)
    }

    @Test
    fun Service_ImportStagesInProjectAndPublishesRequest() {
        projectDir = File(tmp, "project")
        val s = service()
        val details = provider.details(StoreAssetRef("fake", "chair"))
        s.import(details, details.variants.first())
        val request = (events.single() as AssetStoreEvent.ImportRequested).request
        assertEquals(StorePaths.stagingDir(projectDir!!, "fake", "chair", "gltf-1k"), request.stagingDir)
        assertTrue(File(request.stagingDir, "a.gltf").isFile)
        assertEquals(listOf(ImportEntry("a.gltf", "a.gltf")), request.plan.entries)
        assertNull(s.download)
        assertTrue(File(projectDir, "Assets").exists().not(), "the service never writes into Assets")
    }

    @Test
    fun Service_ImportWithoutProjectOrFailingDownload_ReportsError() {
        val s = service()
        val details = provider.details(StoreAssetRef("fake", "chair"))
        s.import(details, details.variants.first())
        assertNotNull(s.downloadError)
        projectDir = File(tmp, "project")
        http.failDownloads = true
        s.import(details, details.variants.first())
        assertNotNull(s.downloadError)
        assertTrue(events.isEmpty())
        assertNull(s.download)
    }

    @Test
    fun Service_ThumbnailsAreCachedOnDisk() {
        val s = service()
        val first = s.thumbnail("https://x/thumb.png")
        val file = s.thumbnail("https://x/thumb.png")
        assertNull(first)
        assertEquals("png", file!!.readText())
        assertEquals(1, http.downloads.size)
        assertNull(s.thumbnail("https://x/missing.png"))
        assertNull(s.thumbnail("https://x/missing.png"))
        assertEquals(2, http.downloads.size)
    }

    @Test
    fun Service_ImportFinishedRefreshesLedger() {
        projectDir = File(tmp, "project")
        AttributionLedger.save(projectDir!!, AttributionLedger().upsert(LedgerEntry(listOf("1"), "fake", "a", title = "A",
            sourceUrl = "u", license = LedgerLicense("CC0-1.0"), retrievedAt = "r")))
        val s = service()
        s.onImportFinished(AssetStoreEvent.ImportFinished("A", true, null))
        assertEquals(1, s.ledger.entries.size)
        assertTrue(s.lastImport!!.success)
    }
}
