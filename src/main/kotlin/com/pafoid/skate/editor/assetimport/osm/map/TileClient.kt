package com.pafoid.skate.editor.assetimport.osm.map

import com.pafoid.skate.editor.assetimport.osm.OsmHttp
import com.pafoid.skate.editor.assetstore.CancelToken
import com.pafoid.skate.editor.assetstore.StoreCancelledException
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** A tile's bytes and where they came from. [offline]: the network failed and an expired cached copy is shown. */
data class TileData(val key: TileKey, val bytes: ByteArray, val fromCache: Boolean, val offline: Boolean = false)

/**
 * Fetches map tiles under the providers' usage policies (AST-17 §3.1), in code rather than docs:
 * - every request carries [OsmHttp.USER_AGENT];
 * - only the tiles asked for (the visible ones) are requested: no prefetching;
 * - at most [MAX_IN_FLIGHT] requests at once; a cancelled token (tile scrolled out of view) is never sent or waits;
 * - cached tiles are served until they expire (`Cache-Control: max-age`, then `Expires`, else 7 days), then
 *   revalidated with `If-None-Match`/`If-Modified-Since` (a 304 keeps the file); never `no-cache`;
 * - offline, expired cached tiles are still returned ([TileData.offline]); after a network failure no request is
 *   sent for [RETRY_AFTER] (no retry storm).
 *
 * Call [fetch] from `JobSystem` workers; decoding and rasterising also happen there, GL upload on the main thread.
 */
class TileClient(
    private val http: OsmHttp,
    private val cache: TileDiskCache,
    private val clock: () -> Instant = Instant::now,
) {
    private val slots = Semaphore(MAX_IN_FLIGHT)

    @Volatile
    private var offlineUntil: Instant = Instant.MIN

    /** True while the last network failure is recent: the widget shows "Map offline: showing cached tiles". */
    val isOffline: Boolean get() = clock().isBefore(offlineUntil)

    /** The tile, or null when it is neither cached nor fetchable now (offline or HTTP error). */
    fun fetch(source: TileSource, key: TileKey, cancel: CancelToken): TileData? {
        if (key.z > source.maxZoom) return null // callers over-zoom from TileKey.ancestorAt(source.maxZoom)
        val cached = cache.read(source, key)
        val now = clock()
        if (cached != null && cached.second.expiresAt > now.toEpochMilli()) return TileData(key, cached.first, fromCache = true)
        if (isOffline) return cached?.let { TileData(key, it.first, fromCache = true, offline = true) }
        if (cancel.isCancelled) throw StoreCancelledException()

        acquire(cancel)
        try {
            if (cancel.isCancelled) throw StoreCancelledException()
            val headers = buildList {
                add("Accept" to if (source.isVector) "application/x-protobuf" else "image/*")
                cached?.second?.etag?.let { add("If-None-Match" to it) }
                cached?.second?.lastModified?.let { add("If-Modified-Since" to it) }
            }
            val result = try {
                http.get(source.url(key), OsmHttp.headers(*headers.toTypedArray()), cancel)
            } catch (e: StoreCancelledException) {
                throw e
            } catch (e: IOException) {
                offlineUntil = clock().plus(RETRY_AFTER)
                return cached?.let { TileData(key, it.first, fromCache = true, offline = true) }
            }
            offlineUntil = Instant.MIN
            return when {
                result.status == 304 && cached != null -> {
                    cache.writeMeta(source, key, meta(result.headers, cached.second))
                    TileData(key, cached.first, fromCache = true)
                }
                result.isSuccess -> {
                    cache.write(source, key, result.body, meta(result.headers, null))
                    TileData(key, result.body, fromCache = false)
                }
                else -> cached?.let { TileData(key, it.first, fromCache = true, offline = true) }
            }
        } finally {
            slots.release()
        }
    }

    private fun acquire(cancel: CancelToken) {
        while (!slots.tryAcquire(50, TimeUnit.MILLISECONDS)) {
            if (cancel.isCancelled) throw StoreCancelledException()
        }
    }

    /** Expiry and validators from the response headers (case-insensitive), keeping [previous] validators on a 304. */
    private fun meta(headers: Map<String, List<String>>, previous: TileEntryMeta?): TileEntryMeta {
        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
        val now = clock()
        val expires = maxAge(header("Cache-Control"))?.let { now.plusSeconds(it) }
            ?: header("Expires")?.let { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull() }
            ?: now.plus(DEFAULT_MAX_AGE)
        return TileEntryMeta(expires.toEpochMilli(), header("ETag") ?: previous?.etag, header("Last-Modified") ?: previous?.lastModified)
    }

    companion object {
        const val MAX_IN_FLIGHT = 4
        val DEFAULT_MAX_AGE: Duration = Duration.ofDays(7)
        val RETRY_AFTER: Duration = Duration.ofSeconds(30)

        /** `max-age` (or `s-maxage`) seconds of a `Cache-Control` value; `no-store`/`no-cache` give 0 (revalidate). */
        fun maxAge(cacheControl: String?): Long? {
            val parts = cacheControl?.split(',')?.map { it.trim().lowercase() } ?: return null
            if ("no-store" in parts || "no-cache" in parts) return 0
            return parts.firstNotNullOfOrNull { p ->
                if (p.startsWith("max-age=")) p.removePrefix("max-age=").toLongOrNull() else null
            }
        }
    }
}
