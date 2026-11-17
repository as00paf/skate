package com.pafoid.skate.editor.assetimport.osm

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** A WGS84 position in degrees. */
@Serializable
data class GeoPoint(val lat: Double, val lon: Double) {
    val isValid: Boolean get() = lat in -90.0..90.0 && lon in -180.0..180.0 && !lat.isNaN() && !lon.isNaN()
}

/** A lat/lon rectangle (south ≤ north, west ≤ east; no antimeridian crossing in v1). */
@Serializable
data class GeoBox(val south: Double, val west: Double, val north: Double, val east: Double) {
    init {
        require(south <= north && west <= east) { "Invalid box $this" }
    }

    val center: GeoPoint get() = GeoPoint((south + north) / 2, (west + east) / 2)

    /** Width (east-west) and height (north-south) in metres, measured through [center]. */
    val sizeMetres: Pair<Double, Double>
        get() {
            val p = LocalProjection(center)
            return p.metresPerDegreeLon * (east - west) to p.metresPerDegreeLat * (north - south)
        }

    val areaKm2: Double get() = sizeMetres.let { (w, h) -> w * h / 1_000_000.0 }

    /** Overpass `(south,west,north,east)` bbox order. */
    fun overpass(): String = "$south,$west,$north,$east"

    /** This box shrunk around its centre to at most [maxKm2] (AST-17 §4.1: 4 km²), keeping its aspect ratio. */
    fun clamped(maxKm2: Double = MAX_AREA_KM2): GeoBox {
        val area = areaKm2
        if (area <= maxKm2 || area <= 0.0) return this
        val k = sqrt(maxKm2 / area)
        val c = center
        val halfLat = (north - south) / 2 * k
        val halfLon = (east - west) / 2 * k
        return GeoBox(c.lat - halfLat, c.lon - halfLon, c.lat + halfLat, c.lon + halfLon)
    }

    companion object {
        const val MAX_AREA_KM2 = 4.0

        /** A [sideMetres] square around [center] (the dialog's size presets). */
        fun square(center: GeoPoint, sideMetres: Double): GeoBox {
            val p = LocalProjection(center)
            val halfLat = sideMetres / 2 / p.metresPerDegreeLat
            val halfLon = sideMetres / 2 / p.metresPerDegreeLon
            return GeoBox(center.lat - halfLat, center.lon - halfLon, center.lat + halfLat, center.lon + halfLon)
        }
    }
}

/**
 * Local metres around [origin] (AST-17 §4.2): equirectangular, `x = R·Δλ·cos φ0`, `z = −R·Δφ`. +Y is up and −Z is
 * north, like the editor's axes. Under 0.1 % error at 2 km.
 */
class LocalProjection(val origin: GeoPoint) {
    val metresPerDegreeLat: Double = EARTH_RADIUS * PI / 180.0
    val metresPerDegreeLon: Double = metresPerDegreeLat * cos(origin.lat * PI / 180.0)

    fun x(lon: Double): Double = (lon - origin.lon) * metresPerDegreeLon
    fun z(lat: Double): Double = -(lat - origin.lat) * metresPerDegreeLat

    fun toGeo(x: Double, z: Double): GeoPoint = GeoPoint(origin.lat - z / metresPerDegreeLat, origin.lon + x / metresPerDegreeLon)

    companion object {
        const val EARTH_RADIUS = 6_371_008.8
    }
}

/** Where a pasted link or coordinates point: a position and, for map links, a zoom. */
data class OsmLocation(val point: GeoPoint, val zoom: Int? = null)

/**
 * Parses what users paste in the "Where" field (AST-17 §3): `https://www.openstreetmap.org/#map=17/48.8566/2.3522`,
 * `…?mlat=48.85&mlon=2.35`, or plain `48.8566, 2.3522`. Null when nothing valid is found. Works offline.
 */
object OsmLink {
    private val MAP_FRAGMENT = Regex("""map=(\d{1,2})/(-?\d+(?:\.\d+)?)/(-?\d+(?:\.\d+)?)""")
    private val COORDINATES = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*[,;\s]\s*(-?\d+(?:\.\d+)?)\s*$""")

    fun parse(text: String): OsmLocation? {
        val input = text.trim()
        if (input.isEmpty()) return null
        MAP_FRAGMENT.find(input)?.let { m ->
            val (zoom, lat, lon) = m.destructured
            return located(lat.toDouble(), lon.toDouble(), zoom.toInt())
        }
        val query = query(input)
        val lat = query["mlat"]?.toDoubleOrNull()
        val lon = query["mlon"]?.toDoubleOrNull()
        if (lat != null && lon != null) return located(lat, lon, query["zoom"]?.toIntOrNull())
        COORDINATES.find(input)?.let { m ->
            val (a, b) = m.destructured
            return located(a.toDouble(), b.toDouble(), null)
        }
        return null
    }

    private fun located(lat: Double, lon: Double, zoom: Int?): OsmLocation? =
        GeoPoint(lat, lon).takeIf { it.isValid }?.let { OsmLocation(it, zoom?.coerceIn(0, 19)) }

    private fun query(url: String): Map<String, String> {
        val q = url.substringAfter('?', "").substringBefore('#')
        if (q.isEmpty()) return emptyMap()
        return q.split('&').mapNotNull { part ->
            val key = part.substringBefore('=', "")
            if (key.isEmpty()) null else key to part.substringAfter('=')
        }.toMap()
    }
}

/** File-name slug of a place name (`Place de la Bastille` → `place_de_la_bastille`), or of [fallback] coordinates. */
fun citySlug(name: String?, fallback: GeoPoint): String {
    val ascii = Normalizer.normalize(name.orEmpty(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    val slug = ascii.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40)
    if (slug.isNotEmpty()) return slug
    fun f(v: Double) = String.format(Locale.ROOT, "%.4f", v).replace('-', 'm').replace('.', '_')
    return "city_${f(fallback.lat)}_${f(fallback.lon)}"
}
