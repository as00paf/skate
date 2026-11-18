package com.pafoid.skate.editor.assetimport.osm.map

import com.pafoid.skate.editor.assetimport.osm.OsmHttp
import com.pafoid.skate.editor.assetimport.osm.map.MvtFixtures.F
import com.pafoid.skate.editor.assetimport.osm.map.MvtFixtures.geometry
import com.pafoid.skate.editor.assetimport.osm.map.MvtFixtures.layer
import com.pafoid.skate.editor.assetimport.osm.map.MvtFixtures.rect
import com.pafoid.skate.editor.assetimport.osm.map.MvtFixtures.tile
import com.pafoid.skate.editor.assetstore.CancelToken
import com.pafoid.skate.editor.assetstore.HttpResult
import com.pafoid.skate.editor.assetstore.StoreCancelledException
import com.pafoid.skate.editor.settings.EditorSettings
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

/** AST-34 (AST-17 §3.1 "Tests with no sockets"): vector tiles, rasteriser, cache rules and usage policy. */
class MapTilesTest {
    @TempDir
    lateinit var dir: File

    private var now = Instant.parse("2026-11-17T10:00:00Z")
    private val clock = { now }
    private val source = TileSource.Vector("https://tiles.example/planet/20261110/{z}/{x}/{y}.pbf")
    private val key = TileKey(14, 8297, 5634)

    /** Answers with [respond]; records every request. */
    private class FakeHttp(var respond: (String, Map<String, String>) -> HttpResult) : OsmHttp {
        val requests = ArrayList<Pair<String, Map<String, String>>>()
        override fun get(url: String, headers: Map<String, String>, cancel: CancelToken): HttpResult {
            synchronized(requests) { requests += url to headers }
            return respond(url, headers)
        }
    }

    private fun ok(body: ByteArray = byteArrayOf(1, 2, 3), headers: Map<String, List<String>> = emptyMap()) = HttpResult(200, body, headers)
    private fun client(http: OsmHttp, maxBytes: Long = TileDiskCache.DEFAULT_MAX_BYTES) =
        TileClient(http, TileDiskCache(File(dir, "tiles"), maxBytes) { now.toEpochMilli() }, clock)

    // --- MVT decoder -------------------------------------------------------------------------------------------

    @Test
    fun Mvt_DecodesLayersFeaturesTagsAndAPolygonWithAHole() {
        val bytes = tile(
            layer("building", listOf(F(3, geometry(listOf(rect(0, 0, 100, 100), rect(25, 75, 75, 25)), closed = true), intArrayOf(0, 0, 1, 1, 2, 2), id = 7)),
                keys = listOf("render_height", "name", "hidden"), values = listOf(12L, "Hall", true)),
            layer("transportation", listOf(F(2, geometry(listOf(listOf(0 to 0, 10 to 0, 10 to 10)), closed = false)), F(1, geometry(listOf(listOf(5 to 5)), closed = false).copyOf(3)))),
        )
        val layers = MvtDecoder.decode(bytes)
        assertEquals(listOf("building", "transportation"), layers.map { it.name })
        assertEquals(4096, layers[0].extent)
        val hall = layers[0].features.single()
        assertEquals(7L, hall.id); assertEquals(MvtType.POLYGON, hall.type)
        assertEquals(mapOf("render_height" to 12L, "name" to "Hall", "hidden" to true), hall.tags)
        assertEquals(2, hall.parts.size, "exterior ring and hole")
        assertEquals(10, hall.parts[0].size, "4 points + ClosePath repeat, x/y pairs")
        assertEquals(hall.parts[0][0], hall.parts[0][8]); assertEquals(hall.parts[0][1], hall.parts[0][9])
        assertArrayEquals(floatArrayOf(25f, 75f), hall.parts[1].copyOf(2), "hole decoded from deltas after the first ring")
        val (line, point) = layers[1].features
        assertEquals(MvtType.LINESTRING, line.type)
        assertArrayEquals(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f), line.parts.single())
        assertEquals(MvtType.POINT, point.type)
        assertArrayEquals(floatArrayOf(5f, 5f), point.parts.single())
    }

    @Test
    fun Mvt_CommandEncodingAndGzippedTiles() {
        // MoveTo(1) 25,17 ; LineTo(2) +3,+6 then -3,-6 encoded with zig-zag (spec example values).
        val parts = MvtDecoder.geometry(intArrayOf(9, 50, 34, 18, 6, 12, 5, 11))
        assertArrayEquals(floatArrayOf(25f, 17f, 28f, 23f, 25f, 17f), parts.single())
        val zipped = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(MvtFixtures.city()) } }.toByteArray()
        assertEquals(listOf("water", "park", "building", "transportation"), MvtDecoder.decode(zipped).map { it.name })
        assertThrows(IllegalArgumentException::class.java) { MvtDecoder.decode(byteArrayOf(0x1a, 0x7f)) }
    }

    // --- Rasteriser --------------------------------------------------------------------------------------------

    @Test
    fun Rasterizer_DrawsWaterParksBuildingsAndRoadsWhereTheyAre() {
        val image = TileRasterizer.render(MvtDecoder.decode(MvtFixtures.city()), zoom = 14)
        assertEquals(TileRasterizer.SIZE, image.width)
        fun rgb(x: Int, y: Int) = image.getRGB(x, y) and 0xffffff
        assertEquals(TileRasterizer.WATER.rgb and 0xffffff, rgb(100, 100))
        assertEquals(TileRasterizer.PARK.rgb and 0xffffff, rgb(410, 410))
        assertEquals(TileRasterizer.BUILDING.rgb and 0xffffff, rgb(390, 100))
        assertEquals(TileRasterizer.MAJOR_ROAD.rgb and 0xffffff, rgb(400, 256))
        assertEquals(TileRasterizer.BACKGROUND.rgb and 0xffffff, rgb(300, 450))
        val low = TileRasterizer.render(MvtDecoder.decode(MvtFixtures.city()), zoom = 12)
        assertEquals(TileRasterizer.BACKGROUND.rgb and 0xffffff, low.getRGB(390, 100) and 0xffffff, "buildings only from z14")
    }

    @Test
    fun Rasterizer_OverZoomsAQuarterOfTheSourceTile() {
        val deep = TileKey(15, 8297 * 2 + 1, 5634 * 2) // top-right child of the z14 tile
        assertEquals(key, deep.ancestorAt(14))
        assertEquals(Triple(1, 1, 0), TileRasterizer.subOf(deep, 14))
        val image = TileRasterizer.render(MvtDecoder.decode(MvtFixtures.city()), 14, sub = TileRasterizer.subOf(deep, 14))
        // The top-right quarter holds the building (2600..3600 of 4096 → 76..324 px at 2x) and no water.
        assertEquals(TileRasterizer.BUILDING.rgb and 0xffffff, image.getRGB(200, 200) and 0xffffff)
        assertFalse((0 until 512 step 16).any { image.getRGB(it, 100) and 0xffffff == TileRasterizer.WATER.rgb and 0xffffff })
    }

    // --- Tile client and cache ----------------------------------------------------------------------------------

    @Test
    fun Client_SendsTheUserAgentAndServesFreshTilesFromTheCache() {
        val http = FakeHttp { _, _ -> ok(headers = mapOf("cache-control" to listOf("public, max-age=3600"))) }
        val c = client(http)
        val first = c.fetch(source, key, CancelToken())!!
        assertFalse(first.fromCache)
        assertEquals("https://tiles.example/planet/20261110/14/8297/5634.pbf", http.requests.single().first)
        assertEquals(OsmHttp.USER_AGENT, http.requests.single().second["User-Agent"])
        now = now.plusSeconds(3599)
        assertTrue(c.fetch(source, key, CancelToken())!!.fromCache)
        assertEquals(1, http.requests.size, "max-age honoured: no second request")
        now = now.plusSeconds(2)
        c.fetch(source, key, CancelToken())
        assertEquals(2, http.requests.size, "expired: asked again")
        assertTrue(http.requests.none { (_, h) -> h.values.any { "no-cache" in it } })
    }

    @Test
    fun Client_DefaultsToSevenDaysAndHonoursExpires() {
        val http = FakeHttp { _, _ -> ok() }
        val c = client(http)
        c.fetch(source, key, CancelToken())
        now = now.plus(Duration.ofDays(7)).minusSeconds(1)
        c.fetch(source, key, CancelToken())
        assertEquals(1, http.requests.size)
        now = now.plusSeconds(2)
        http.respond = { _, _ -> ok(headers = mapOf("Expires" to listOf("Wed, 25 Nov 2026 10:00:00 GMT"))) }
        c.fetch(source, key, CancelToken())
        assertEquals(2, http.requests.size)
        now = Instant.parse("2026-11-25T09:59:00Z")
        c.fetch(source, key, CancelToken())
        assertEquals(2, http.requests.size, "Expires honoured")
    }

    @Test
    fun Client_RevalidatesExpiredTilesConditionallyAnd304KeepsTheFile() {
        val http = FakeHttp { _, _ -> ok(byteArrayOf(9, 9), mapOf("ETag" to listOf("\"v1\""), "Last-Modified" to listOf("Mon, 10 Nov 2026 00:00:00 GMT"), "Cache-Control" to listOf("max-age=60"))) }
        val c = client(http)
        c.fetch(source, key, CancelToken())
        now = now.plusSeconds(61)
        http.respond = { _, _ -> HttpResult(304, ByteArray(0), mapOf("Cache-Control" to listOf("max-age=60"))) }
        val again = c.fetch(source, key, CancelToken())!!
        val headers = http.requests[1].second
        assertEquals("\"v1\"", headers["If-None-Match"])
        assertEquals("Mon, 10 Nov 2026 00:00:00 GMT", headers["If-Modified-Since"])
        assertArrayEquals(byteArrayOf(9, 9), again.bytes)
        now = now.plusSeconds(30)
        c.fetch(source, key, CancelToken())
        assertEquals(2, http.requests.size, "the 304 renewed the expiry")
    }

    @Test
    fun Client_OfflineShowsExpiredTilesAndDoesNotRetryFor30Seconds() {
        val http = FakeHttp { _, _ -> ok(byteArrayOf(4), mapOf("Cache-Control" to listOf("max-age=10"))) }
        val c = client(http)
        c.fetch(source, key, CancelToken())
        now = now.plusSeconds(20)
        http.respond = { _, _ -> throw IOException("no network") }
        val stale = c.fetch(source, key, CancelToken())!!
        assertTrue(stale.offline && c.isOffline)
        assertArrayEquals(byteArrayOf(4), stale.bytes)
        val other = TileKey(14, 1, 1)
        assertNull(c.fetch(source, other, CancelToken()), "missing tile while offline")
        assertEquals(2, http.requests.size, "no request during the 30 s back-off")
        now = now.plusSeconds(31)
        http.respond = { _, _ -> ok() }
        assertFalse(c.fetch(source, other, CancelToken())!!.offline)
        assertFalse(c.isOffline)
    }

    @Test
    fun Client_NeverSendsCancelledOrTooDeepRequests() {
        val http = FakeHttp { _, _ -> ok() }
        val c = client(http)
        val cancelled = CancelToken().also { it.cancel() }
        assertThrows(StoreCancelledException::class.java) { c.fetch(source, key, cancelled) }
        assertNull(c.fetch(source, TileKey(15, 0, 0), CancelToken()), "z15 > OpenFreeMap's z14: callers over-zoom")
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun Client_KeepsAtMostFourRequestsInFlight() {
        val inFlight = AtomicInteger(); val peak = AtomicInteger()
        val release = CountDownLatch(1)
        val http = FakeHttp { _, _ ->
            peak.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            release.await(5, TimeUnit.SECONDS)
            inFlight.decrementAndGet()
            ok()
        }
        val c = client(http)
        val pool = Executors.newFixedThreadPool(10)
        val tokens = (0 until 10).map { CancelToken() }
        val futures = (0 until 10).map { i -> pool.submit<TileData?> { runCatching { c.fetch(source, TileKey(14, i, 0), tokens[i]) }.getOrNull() } }
        Thread.sleep(300)
        assertEquals(TileClient.MAX_IN_FLIGHT, peak.get())
        tokens.drop(8).forEach { it.cancel() } // two tiles scroll out of view while waiting
        release.countDown()
        futures.forEach { it.get(5, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(TileClient.MAX_IN_FLIGHT, peak.get())
        assertEquals(8, http.requests.size, "the cancelled waiters were never sent")
    }

    @Test
    fun Cache_EvictsTheOldestTilesOverItsCapAndClears() {
        val cache = TileDiskCache(File(dir, "capped"), maxBytes = 25) { now.toEpochMilli() }
        (0 until 3).forEach { i ->
            cache.write(source, TileKey(14, i, 0), ByteArray(10), TileEntryMeta(Long.MAX_VALUE))
            now = now.plusSeconds(10)
        }
        assertNull(cache.read(source, TileKey(14, 0, 0)), "oldest evicted")
        assertNotNull(cache.read(source, TileKey(14, 2, 0)))
        assertTrue(cache.sizeBytes() <= 25)
        cache.clear()
        assertEquals(0, cache.sizeBytes())
    }

    @Test
    fun TileJson_IsCachedForADayAndUsedOfflineWhenExpired() {
        val text = """{"tilejson":"3.0.0","tiles":["https://tiles.openfreemap.org/planet/20261110_001001_pt/{z}/{x}/{y}.pbf"],"maxzoom":14}"""
        val http = FakeHttp { _, _ -> ok(text.toByteArray()) }
        val resolver = TileJsonResolver(http, File(dir, "tj.json"), clock)
        val vector = resolver.resolve()
        assertEquals(14, vector.maxZoom)
        assertEquals("https://tiles.openfreemap.org/planet/20261110_001001_pt/14/1/2.pbf", vector.url(TileKey(14, 1, 2)))
        assertEquals(TileSource.OPENFREEMAP_TILEJSON, http.requests.single().first)
        now = now.plus(Duration.ofHours(23))
        resolver.resolve()
        assertEquals(1, http.requests.size)
        now = now.plus(Duration.ofHours(2))
        http.respond = { _, _ -> throw IOException("offline") }
        assertEquals(vector, resolver.resolve(), "expired copy used offline")
    }

    @Test
    fun MapSettings_RasterTemplateNeedsPlaceholdersAndAttribution() {
        assertNull(MapSettings.validate(null, null), "blank: OpenFreeMap")
        assertNotNull(MapSettings.validate("https://tiles.example/{z}/{x}.png", "© Me"))
        assertNotNull(MapSettings.validate("https://tiles.example/{z}/{x}/{y}.png", "  "), "attribution is mandatory")
        assertNotNull(MapSettings.validate("ftp://tiles/{z}/{x}/{y}.png", "© Me"))
        val raster = MapSettings.rasterSource("https://tiles.example/{z}/{x}/{y}.png", "© Example")!!
        assertEquals("© Example", raster.attribution)
        assertNull(MapSettings.rasterSource("https://tiles.example/{z}/{x}/{y}.png", null))
    }

    @Test
    fun Services_RenderVectorTilesOverZoomedAndShowTheRightAttribution() {
        val tileJson = """{"tiles":["https://tiles.example/p/{z}/{x}/{y}.pbf"],"maxzoom":14}"""
        val http = FakeHttp { url, _ -> if (url.endsWith(".pbf")) ok(MvtFixtures.city()) else ok(tileJson.toByteArray()) }
        var settings = EditorSettings()
        val services = OsmMapServices({ settings }, http, File(dir, "svc"), clock)
        val image = services.image(TileKey(16, 8297 * 4, 5634 * 4), CancelToken())!!
        assertEquals(512, image.width)
        assertEquals("https://tiles.example/p/14/8297/5634.pbf", http.requests.last().first, "z16 drawn from its z14 ancestor")
        assertEquals(TileSource.OPENFREEMAP_ATTRIBUTION, services.attribution())

        settings = settings.copy(osmTileUrl = "https://raster.example/{z}/{x}/{y}.png", osmTileAttribution = "© Raster Co")
        assertEquals("© Raster Co", services.attribution())
        assertTrue(services.source() is TileSource.Raster)
        services.clearCache()
        assertFalse(File(dir, "svc").walkTopDown().any { it.isFile && it.path.contains("openfreemap-") })
    }
}
