package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.editor.events.AssetStoreEvent
import com.pafoid.skate.engine.events.Event
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/** Where the service runs work: blocking calls on an IO worker, state changes on the main thread. */
interface StoreJobs {
    fun io(block: () -> Unit)
    fun main(block: () -> Unit)
}

/** One download in progress; [done]/[total] are written by the worker and read by the window. */
class StoreDownload(val title: String, val cancel: CancelToken) {
    @Volatile var done: Long = 0
    @Volatile var total: Long? = null
    val fraction: Float get() = total?.takeIf { it > 0 }?.let { (done.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
}

/**
 * State and actions behind the Asset Store window (AST-16 §9). Network work runs on [StoreJobs.io]; results are applied
 * on [StoreJobs.main], so the window reads plain fields. The window never mutates the project: a finished download is
 * published as [AssetStoreEvent.ImportRequested] and imported by a command.
 */
class AssetStoreService(
    val registry: AssetStoreRegistry,
    private val http: StoreHttp,
    private val jobs: StoreJobs,
    private val publish: (Event) -> Unit,
    private val projectDir: () -> File?,
    private val cacheDir: File = StorePaths.userCacheDir(),
) {
    var providerId: String = registry.providers.first().id
        private set
    var query: StoreQuery = StoreQuery()
        private set

    var results: List<StoreAssetSummary> = emptyList()
        private set
    var total: Int? = null
        private set
    var nextCursor: String? = null
        private set
    var searching = false
        private set
    var searchError: String? = null
        private set

    var selected: StoreAssetSummary? = null
        private set
    var details: StoreAssetDetails? = null
        private set
    var detailsLoading = false
        private set
    var detailsError: String? = null
        private set

    var download: StoreDownload? = null
        private set
    var lastImport: AssetStoreEvent.ImportFinished? = null
        private set
    var downloadError: String? = null
        private set

    var ledger: AttributionLedger = AttributionLedger()
        private set

    private var searchGeneration = 0
    private val thumbnails = ConcurrentHashMap<String, File>()
    private val thumbnailsPending = ConcurrentHashMap.newKeySet<String>()
    private val thumbnailsFailed = ConcurrentHashMap.newKeySet<String>()
    private val thumbnailSlots = Semaphore(4)
    private val downloader = StagingDownloader(http)

    val provider: AssetStoreProvider get() = registry.get(providerId)!!

    fun selectProvider(id: String) {
        if (id == providerId || registry.get(id) == null) return
        providerId = id
        val kind = query.kind.takeIf { it in provider.kinds } ?: provider.kinds.first()
        search(query.copy(kind = kind))
    }

    /** New search from the first page; earlier in-flight searches are ignored when they return. */
    fun search(newQuery: StoreQuery) {
        query = newQuery.copy(cursor = null)
        results = emptyList()
        total = null
        nextCursor = null
        runSearch(query, append = false)
    }

    fun loadMore() {
        val cursor = nextCursor ?: return
        if (searching) return
        runSearch(query.copy(cursor = cursor), append = true)
    }

    private fun runSearch(q: StoreQuery, append: Boolean) {
        val generation = ++searchGeneration
        val p = provider
        searching = true
        searchError = null
        jobs.io {
            val result = runCatching { p.search(q) }
            jobs.main {
                if (generation != searchGeneration) return@main
                searching = false
                result.onSuccess { page ->
                    results = if (append) results + page.items else page.items
                    total = page.total
                    nextCursor = page.nextCursor
                }.onFailure { searchError = it.message ?: it.javaClass.simpleName }
            }
        }
    }

    fun select(item: StoreAssetSummary) {
        if (selected?.ref == item.ref && (details != null || detailsLoading)) return
        selected = item
        details = null
        detailsError = null
        detailsLoading = true
        val p = provider
        jobs.io {
            val result = runCatching { p.details(item.ref) }
            jobs.main {
                if (selected?.ref != item.ref) return@main
                detailsLoading = false
                result.onSuccess { details = it }.onFailure { detailsError = it.message ?: it.javaClass.simpleName }
            }
        }
    }

    /** Downloads [variant] into the project's staging folder, then asks for the import command. One at a time. */
    fun import(target: StoreAssetDetails, variant: StoreVariant) {
        if (download != null) return
        val project = projectDir() ?: run { downloadError = "No project is open"; return }
        val ref = target.summary.ref
        val p = registry.get(ref.providerId) ?: return
        val job = StoreDownload(target.summary.title, CancelToken())
        download = job
        downloadError = null
        jobs.io {
            val result = runCatching {
                StorePaths.ensureHiddenDir(project)
                val staging = StorePaths.stagingDir(project, ref.providerId, ref.sourceId, variant.id)
                val plan = p.resolveDownload(target, variant)
                val staged = downloader.download(plan, staging, job.cancel) { done, total ->
                    job.done = done
                    job.total = total
                }
                if (job.cancel.isCancelled) throw StoreCancelledException()
                val prepared = p.postProcess(target, variant, staging, staged)
                StoreImportRequest(target, variant, staging, p.importPlan(target, variant, prepared))
            }
            jobs.main {
                download = null
                result.onSuccess { publish(AssetStoreEvent.ImportRequested(it)) }
                    .onFailure { if (it !is StoreCancelledException) downloadError = it.message ?: it.javaClass.simpleName }
            }
        }
    }

    fun cancelDownload() {
        download?.cancel?.cancel()
    }

    /** Called with the handler's result (main thread). */
    fun onImportFinished(event: AssetStoreEvent.ImportFinished) {
        lastImport = event
        refreshLedger()
    }

    fun refreshLedger() {
        val dir = projectDir()
        ledger = dir?.let { runCatching { AttributionLedger.load(it) }.getOrNull() } ?: AttributionLedger()
    }

    /**
     * Cached thumbnail file for [url], or null while it downloads (started on first call) or after it failed. The file
     * is named `<sha1(url)>.<ext>` with the extension from the response content type (`png` when unknown).
     */
    fun thumbnail(url: String?): File? {
        if (url == null) return null
        thumbnails[url]?.let { return it }
        if (url in thumbnailsFailed) return null
        val dir = File(cacheDir, "thumbs")
        val key = sha1(url)
        dir.listFiles { f -> f.isFile && f.name.startsWith("$key.") && !f.name.endsWith(".part") }?.firstOrNull()?.let {
            thumbnails[url] = it
            return it
        }
        if (!thumbnailsPending.add(url)) return null
        jobs.io {
            thumbnailSlots.acquire()
            val file = try {
                val part = File(dir, "$key.part")
                runCatching {
                    val type = http.download(url, emptyMap(), part, CancelToken())
                    val target = File(dir, "$key.${StoreHttp.imageExtension(type) ?: "png"}")
                    target.takeIf { part.renameTo(it) }
                }.getOrNull().also { if (it == null) part.delete() }
            } finally {
                thumbnailSlots.release()
            }
            jobs.main {
                thumbnailsPending.remove(url)
                if (file != null) thumbnails[url] = file else thumbnailsFailed += url
            }
        }
        return null
    }

    companion object {
        fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
