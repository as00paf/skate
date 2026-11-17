package com.pafoid.skate.editor.assetimport.osm

import com.pafoid.skate.editor.assetstore.CancelToken
import com.pafoid.skate.editor.assetstore.HttpResult
import com.pafoid.skate.editor.assetstore.StoreHttpException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import kotlin.math.abs

/** AST-34 (AST-17 §3, §4.1, §4.2): link parsing, projection, Overpass query and snapshot parsing, no sockets. */
class OsmDataTest {
    companion object {
        val CENTER = GeoPoint(48.8566, 2.3522)
        val BOX = GeoBox.square(CENTER, 400.0)
        fun fixture(): String = OsmDataTest::class.java.getResource("/osm/overpass_small.json")!!.readText()
    }

    @Test
    fun OsmLink_ParsesMapLinksMarkerLinksAndCoordinates() {
        assertEquals(OsmLocation(GeoPoint(48.8566, 2.3522), 17), OsmLink.parse("https://www.openstreetmap.org/#map=17/48.8566/2.3522"))
        assertEquals(OsmLocation(GeoPoint(45.5, -73.56), 16), OsmLink.parse("https://www.openstreetmap.org/?mlat=45.5&mlon=-73.56#map=16/45.5/-73.56"))
        assertEquals(OsmLocation(GeoPoint(45.5, -73.56), null), OsmLink.parse("https://www.openstreetmap.org/?mlat=45.5&mlon=-73.56"))
        assertEquals(OsmLocation(GeoPoint(-33.8688, 151.2093)), OsmLink.parse("  -33.8688, 151.2093 "))
        assertNull(OsmLink.parse("Paris"))
        assertNull(OsmLink.parse("95, 10"), "latitude out of range")
        assertNull(OsmLink.parse(""))
    }

    @Test
    fun Projection_RoundTripsAndPointsNorthAlongMinusZ() {
        val p = LocalProjection(CENTER)
        val north = GeoPoint(CENTER.lat + 0.001, CENTER.lon)
        assertTrue(p.z(north.lat) < 0, "north is -Z")
        assertTrue(p.x(CENTER.lon + 0.001) > 0, "east is +X")
        val back = p.toGeo(p.x(2.36), p.z(48.86))
        assertEquals(48.86, back.lat, 1e-9)
        assertEquals(2.36, back.lon, 1e-9)
        val (w, h) = BOX.sizeMetres
        assertEquals(400.0, w, 0.5)
        assertEquals(400.0, h, 0.5)
    }

    @Test
    fun GeoBox_IsClampedTo4SquareKilometres() {
        val big = GeoBox.square(CENTER, 5000.0)
        assertEquals(25.0, big.areaKm2, 0.1)
        val clamped = big.clamped()
        assertEquals(GeoBox.MAX_AREA_KM2, clamped.areaKm2, 0.01)
        assertEquals(CENTER.lat, clamped.center.lat, 1e-9)
        assertSame(BOX, BOX.clamped(), "small boxes are unchanged")
    }

    @Test
    fun CitySlug_FromNamesOrCoordinates() {
        assertEquals("place_de_la_bastille", citySlug("Place de la Bastille", CENTER))
        assertEquals("montreal_qc", citySlug("Montréal, QC", CENTER))
        assertEquals("city_48_8566_2_3522", citySlug(null, CENTER))
        assertEquals("city_m33_8688_151_2093", citySlug("  ", GeoPoint(-33.8688, 151.2093)))
    }

    @Test
    fun Parser_ReadsTheFixtureIntoBuildingsRoadsAndSurfaces() {
        val data = OsmParser.parse(fixture())
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), data.buildings.map { it.id }.sorted())
        assertEquals(listOf(10L, 11L, 12L, 13L), data.roads.map { it.id }.sorted())
        assertEquals(listOf(20L, 21L), data.surfaces.map { it.id }.sorted())
        assertEquals("2026-11-10T12:00:00Z", data.timestamp)
        val courtyard = data.areas.single { it.id == 3L }
        assertEquals(1, courtyard.outer.size, "outer ring assembled from two member ways")
        assertEquals(1, courtyard.holes.size)
        assertEquals(courtyard.outer[0].first(), courtyard.outer[0].last())
    }

    @Test
    fun RingAssembler_JoinsReversedSegmentsAndDropsOpenChains() {
        val a = GeoPoint(0.0, 0.0); val b = GeoPoint(0.0, 1.0); val c = GeoPoint(1.0, 1.0); val d = GeoPoint(5.0, 5.0)
        val rings = RingAssembler.join(listOf(listOf(a, b), listOf(a, c).reversed(), listOf(b, c), listOf(d, GeoPoint(6.0, 6.0))))
        assertEquals(1, rings.size)
        assertEquals(4, rings[0].size)
        assertEquals(rings[0].first(), rings[0].last())
    }

    @Test
    fun Tags_HeightsAndWidths() {
        assertEquals(12.0, OsmTags.height(mapOf("height" to "12 m")))
        assertEquals(12.0, OsmTags.height(mapOf("building:levels" to "4")))
        assertEquals(OsmTags.DEFAULT_HEIGHT, OsmTags.height(mapOf("building" to "yes")))
        assertEquals(12.192, OsmTags.height(mapOf("height" to "40'")), 1e-3)
        assertEquals(3.0, OsmTags.minHeight(mapOf("min_height" to "3")))
        assertEquals(12.0, OsmTags.width(mapOf("width" to "12")))
        assertNull(OsmTags.width(mapOf("width" to "wide")))
        assertEquals(RoadClass.PATH, OsmTags.roadClass(mapOf("highway" to "footway")))
        assertNull(OsmTags.roadClass(mapOf("highway" to "pedestrian", "area" to "yes")), "pedestrian areas are plazas")
        assertEquals(CityMaterial.PLAZA, OsmTags.surface(mapOf("highway" to "pedestrian", "area" to "yes")))
    }

    private class FakeHttp(val result: HttpResult) : OsmHttp {
        val urls = ArrayList<String>()
        val headers = ArrayList<Map<String, String>>()
        var token: CancelToken? = null
        override fun get(url: String, headers: Map<String, String>, cancel: CancelToken): HttpResult {
            urls += url; this.headers += headers; token = cancel
            return result
        }
    }

    @Test
    fun Overpass_SendsOneBoxQueryWithTheUserAgentAndReturnsTheSnapshot() {
        val http = FakeHttp(HttpResult(200, fixture().toByteArray()))
        val cancel = CancelToken()
        val text = OverpassClient(http, "https://example.test/api").fetch(BOX, cancel)
        assertEquals(fixture(), text)
        assertEquals(1, http.urls.size)
        val query = URLDecoder.decode(http.urls[0].substringAfter("data="), Charsets.UTF_8)
        assertTrue(http.urls[0].startsWith("https://example.test/api?data="))
        assertTrue("[bbox:${BOX.overpass()}]" in query && "out geom;" in query, query)
        assertEquals("SkateEngine/0.1 (+https://github.com/as00paf/skate)", http.headers[0]["User-Agent"])
        assertSame(cancel, http.token)
    }

    @Test
    fun Overpass_ClampsHugeBoxesAndReportsHttpAndQueryErrors() {
        val ok = FakeHttp(HttpResult(200, """{"elements":[]}""".toByteArray()))
        OverpassClient(ok).fetch(GeoBox.square(CENTER, 10_000.0))
        val sent = URLDecoder.decode(ok.urls[0].substringAfter("data="), Charsets.UTF_8)
        val bbox = Regex("""bbox:([^\]]+)""").find(sent)!!.groupValues[1].split(',').map { it.toDouble() }
        assertTrue(abs(GeoBox(bbox[0], bbox[1], bbox[2], bbox[3]).areaKm2 - GeoBox.MAX_AREA_KM2) < 0.01)

        val busy = assertThrows(StoreHttpException::class.java) { OverpassClient(FakeHttp(HttpResult(429, ByteArray(0)))).fetch(BOX) }
        assertEquals(429, busy.status)
        assertThrows(StoreHttpException::class.java) {
            OverpassClient(FakeHttp(HttpResult(200, """{"remark":"runtime error: Query timed out"}""".toByteArray()))).fetch(BOX)
        }
    }
}
