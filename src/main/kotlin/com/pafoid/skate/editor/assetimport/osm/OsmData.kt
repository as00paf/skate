package com.pafoid.skate.editor.assetimport.osm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** An open or closed OSM way with inline geometry (`out geom`). */
data class OsmWay(val id: Long, val tags: Map<String, String>, val points: List<GeoPoint>) {
    val isClosed: Boolean get() = points.size >= 4 && points.first() == points.last()
}

/** A polygon: a closed way, or an assembled multipolygon relation. Rings are closed (first == last). */
data class OsmArea(val id: Long, val tags: Map<String, String>, val outer: List<List<GeoPoint>>, val holes: List<List<GeoPoint>> = emptyList())

/** What a snapshot holds, split the way the generator uses it. */
data class OsmData(
    val lines: List<OsmWay>,
    val areas: List<OsmArea>,
    /** `osm3s.timestamp_osm_base` of the snapshot, when present (ISO-8601). */
    val timestamp: String? = null,
) {
    val buildings: List<OsmArea> get() = areas.filter { OsmTags.isBuilding(it.tags) }
    val roads: List<OsmWay> get() = lines.filter { OsmTags.roadClass(it.tags) != null }
    val surfaces: List<OsmArea> get() = areas.filter { !OsmTags.isBuilding(it.tags) && OsmTags.surface(it.tags) != null }
}

/** Which OSM tags the generator reads (AST-17 §1). */
object OsmTags {
    fun isBuilding(tags: Map<String, String>): Boolean = tags["building"].let { it != null && it != "no" } || tags.containsKey("building:part")

    /** Road ribbon half-width class, or null when [tags] are not a drawable road/path. */
    fun roadClass(tags: Map<String, String>): RoadClass? {
        val highway = tags["highway"] ?: return null
        if (tags["area"] == "yes") return null
        return RoadClass.of(highway)
    }

    /** Flat surface category for areas, or null. */
    fun surface(tags: Map<String, String>): CityMaterial? = when {
        tags["natural"] == "water" || tags["waterway"] == "riverbank" || tags["landuse"] == "reservoir" -> CityMaterial.WATER
        tags["leisure"] == "park" || tags["leisure"] == "garden" || tags["landuse"] in GRASS_LANDUSE -> CityMaterial.GRASS
        tags.containsKey("area:highway") || tags["place"] == "square" || (tags["highway"] != null && tags["area"] == "yes") -> CityMaterial.PLAZA
        else -> null
    }

    private val GRASS_LANDUSE = setOf("grass", "recreation_ground", "village_green", "meadow")

    /** Height in metres: `height`, else `building:levels` × 3 m, else 9 m (AST-17 §4.3). */
    fun height(tags: Map<String, String>): Double =
        metres(tags["height"]) ?: tags["building:levels"]?.toDoubleOrNull()?.takeIf { it > 0 }?.let { it * LEVEL_HEIGHT } ?: DEFAULT_HEIGHT

    fun minHeight(tags: Map<String, String>): Double =
        metres(tags["min_height"]) ?: tags["building:min_level"]?.toDoubleOrNull()?.let { it * LEVEL_HEIGHT } ?: 0.0

    /** `width` tag in metres, when set and sane. */
    fun width(tags: Map<String, String>): Double? = metres(tags["width"])?.takeIf { it in 0.5..60.0 }

    /** `12`, `12 m`, `12.5m`; feet (`40'`) are converted. */
    fun metres(value: String?): Double? {
        val v = value?.trim()?.lowercase() ?: return null
        if (v.endsWith("'") || v.endsWith("ft")) return v.trimEnd('\'', 'f', 't', ' ').toDoubleOrNull()?.let { it * 0.3048 }?.takeIf { it > 0 }
        return v.removeSuffix("m").trim().toDoubleOrNull()?.takeIf { it > 0 && it < 1000 }
    }

    const val LEVEL_HEIGHT = 3.0
    const val DEFAULT_HEIGHT = 9.0
}

/** Road ribbon widths per `highway` class (motorway 7 m … footway 1 m half-width). */
enum class RoadClass(val halfWidth: Double, val material: CityMaterial, val minor: Boolean = false) {
    MOTORWAY(7.0, CityMaterial.ROAD),
    PRIMARY(5.0, CityMaterial.ROAD),
    SECONDARY(4.5, CityMaterial.ROAD),
    TERTIARY(4.0, CityMaterial.ROAD),
    RESIDENTIAL(3.0, CityMaterial.ROAD),
    SERVICE(2.0, CityMaterial.ROAD, minor = true),
    PEDESTRIAN(3.0, CityMaterial.PATH),
    PATH(1.0, CityMaterial.PATH, minor = true);

    companion object {
        fun of(highway: String): RoadClass? = when (highway) {
            "motorway", "trunk", "motorway_link", "trunk_link" -> MOTORWAY
            "primary", "primary_link" -> PRIMARY
            "secondary", "secondary_link" -> SECONDARY
            "tertiary", "tertiary_link" -> TERTIARY
            "residential", "unclassified", "living_street", "road" -> RESIDENTIAL
            "service", "track" -> SERVICE
            "pedestrian" -> PEDESTRIAN
            "footway", "path", "cycleway", "steps", "bridleway", "corridor" -> PATH
            else -> null
        }
    }
}

/** The generated `.skmat` categories (AST-17 §4.7, §5); also the mesh parts of a chunk. */
enum class CityMaterial(val id: String) {
    WALL("wall"), ROOF("roof"), ROAD("road"), PATH("path"), GRASS("grass"), WATER("water"), PLAZA("plaza"), GROUND("ground");
}

/**
 * Reads an Overpass JSON response (`[out:json]` … `out geom;`) into [OsmData]. Closed ways with building or surface
 * tags become areas; multipolygon relations are assembled from their member ways ([RingAssembler]).
 */
object OsmParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): OsmData {
        val root = json.parseToJsonElement(text).jsonObject
        val lines = ArrayList<OsmWay>()
        val areas = ArrayList<OsmArea>()
        (root["elements"] as? JsonArray).orEmpty().forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val tags = tags(obj)
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "way" -> {
                    val way = OsmWay(obj["id"]?.jsonPrimitive?.long ?: 0, tags, geometry(obj))
                    if (way.points.size < 2) return@forEach
                    if (way.isClosed && isAreaTagged(tags)) areas += OsmArea(way.id, tags, listOf(way.points))
                    else lines += way
                }
                "relation" -> relation(obj, tags)?.let { areas += it }
            }
        }
        val timestamp = (root["osm3s"] as? JsonObject)?.get("timestamp_osm_base")?.jsonPrimitive?.contentOrNull
        return OsmData(lines, areas, timestamp)
    }

    private fun isAreaTagged(tags: Map<String, String>): Boolean =
        OsmTags.isBuilding(tags) || OsmTags.surface(tags) != null

    private fun relation(obj: JsonObject, tags: Map<String, String>): OsmArea? {
        if (tags["type"] != "multipolygon" || !isAreaTagged(tags)) return null
        val members = (obj["members"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter {
            it["type"]?.jsonPrimitive?.contentOrNull == "way"
        }
        fun rings(role: String) = RingAssembler.join(members.filter { (it["role"]?.jsonPrimitive?.contentOrNull ?: "") == role }.map(::geometry))
        val outer = rings("outer") + RingAssembler.join(members.filter { (it["role"]?.jsonPrimitive?.contentOrNull ?: "") == "" }.map(::geometry))
        if (outer.isEmpty()) return null
        return OsmArea(obj["id"]?.jsonPrimitive?.long ?: 0, tags - "type", outer, rings("inner"))
    }

    private fun tags(obj: JsonObject): Map<String, String> =
        (obj["tags"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()

    private fun geometry(obj: JsonObject): List<GeoPoint> =
        (obj["geometry"] as? JsonArray).orEmpty().mapNotNull { p ->
            val o = p as? JsonObject ?: return@mapNotNull null // `null` entries: nodes outside the bbox clip
            val lat = o["lat"]?.jsonPrimitive?.double ?: return@mapNotNull null
            val lon = o["lon"]?.jsonPrimitive?.double ?: return@mapNotNull null
            GeoPoint(lat, lon)
        }
}

/** Joins multipolygon member ways that share end points into closed rings; unclosable chains are dropped. */
object RingAssembler {
    fun join(segments: List<List<GeoPoint>>): List<List<GeoPoint>> {
        val open = segments.filter { it.size >= 2 }.map { it.toMutableList() }.toMutableList()
        val rings = ArrayList<List<GeoPoint>>()
        while (open.isNotEmpty()) {
            val ring = open.removeAt(0)
            var grown = true
            while (ring.first() != ring.last() && grown) {
                grown = false
                val i = open.indexOfFirst { it.first() == ring.last() || it.last() == ring.last() }
                if (i >= 0) {
                    val next = open.removeAt(i)
                    ring += (if (next.first() == ring.last()) next else next.reversed()).drop(1)
                    grown = true
                }
            }
            if (ring.size >= 4 && ring.first() == ring.last()) rings += ring
        }
        return rings
    }
}
