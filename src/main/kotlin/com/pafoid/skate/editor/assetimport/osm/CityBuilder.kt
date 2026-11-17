package com.pafoid.skate.editor.assetimport.osm

import com.pafoid.skate.engine.assets.gltf.GlbMesh
import org.locationtech.jts.algorithm.MinimumDiameter
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Polygon
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

/** What to generate (the dialog's Include, Simplify and Colliders choices, AST-17 §3). */
data class CityOptions(
    val buildings: Boolean = true,
    val roads: Boolean = true,
    val surfaces: Boolean = true,
    val ground: Boolean = true,
    val simplify: SimplifyLevel = SimplifyLevel.MEDIUM,
    val buildingColliders: Boolean = true,
    val groundColliders: Boolean = true,
    val chunkSize: Double = DEFAULT_CHUNK_SIZE,
) {
    companion object {
        /** 256 m: a multiple of the REN-28 terrain chunk (64 quads × 1 m) so both grids line up (§4.4). */
        const val DEFAULT_CHUNK_SIZE = 256.0
    }
}

/**
 * A static box collider for the Simple physics backend (AST-17 §4.6, answer 4): [center] and [halfExtents] in local
 * metres, rotated by [yawDegrees] about +Y (local +X along `(cos θ, 0, −sin θ)`).
 */
data class CityBox(val name: String, val center: DoubleArray, val halfExtents: DoubleArray, val yawDegrees: Double)

/** One chunk: render parts per material, the collision triangles, and the Simple-backend boxes. */
class CityChunk(
    val x: Int,
    val z: Int,
    val bounds: Envelope,
    val parts: Map<CityMaterial, GlbMesh>,
    val collision: GlbMesh?,
    val boxes: List<CityBox>,
    val buildingCount: Int,
) {
    val name: String get() = "chunk_${x}_${z}"
    val triangleCount: Int get() = parts.values.sumOf { it.triangleCount }
}

/** Counts shown before generating (§4.5): computed on the snapshot, no files written. */
data class CityStats(val buildings: Int, val roads: Int, val surfaces: Int, val chunks: Int, val triangles: Int, val boxes: Int)

/**
 * Builds the chunks of a city from [data] inside [box] (AST-17 §4.3–4.6). The origin is [box]'s centre. A building
 * belongs to the chunk holding its centroid (kept whole); roads, surfaces and the ground are clipped at chunk
 * borders, so each chunk is self-contained. The collision mesh uses Medium simplification at least and no layer
 * offsets. Pure: no files, no GL.
 */
class CityBuilder(private val data: OsmData, private val box: GeoBox, private val options: CityOptions = CityOptions()) {
    val projection = LocalProjection(box.center)
    private val extent: Envelope = run {
        val p = projection
        Envelope(p.x(box.west), p.x(box.east), p.z(box.north), p.z(box.south))
    }

    private class Building(val polygon: Polygon, val base: Double, val top: Double)

    private fun buildings(level: SimplifyLevel): List<Building> {
        if (!options.buildings) return emptyList()
        return data.buildings.flatMap { area ->
            val top = OsmTags.height(area.tags)
            val base = OsmTags.minHeight(area.tags).coerceAtMost(top)
            if (top < level.minBuildingHeight) return@flatMap emptyList()
            CityGeometry.polygons(area, projection)
                .flatMap { CityGeometry.polygonsOf(CityGeometry.simplify(it, level)) }
                .filter { it.area >= level.minBuildingArea && extent.contains(it.centroid.coordinate) }
                .map { Building(it, base, top) }
        }
    }

    /** Ribbons per material, unioned (junctions without overlapping triangles) and clipped to the city box. */
    private fun roads(level: SimplifyLevel): Map<CityMaterial, Geometry> {
        if (!options.roads) return emptyMap()
        val clip = CityGeometry.square(extent)
        return data.roads.mapNotNull { way ->
            val cls = OsmTags.roadClass(way.tags) ?: return@mapNotNull null
            if (level.dropMinorRoads && cls.minor) return@mapNotNull null
            val line = CityGeometry.line(way, projection) ?: return@mapNotNull null
            if (cls.material == CityMaterial.PATH && line.length < level.minPathLength) return@mapNotNull null
            val simplified = CityGeometry.simplify(line, level)
            val half = OsmTags.width(way.tags)?.div(2) ?: cls.halfWidth
            cls.material to CityGeometry.ribbon(simplified as? org.locationtech.jts.geom.LineString ?: line, half)
        }.groupBy({ it.first }, { it.second }).mapValues { (_, g) -> CityGeometry.union(g).intersection(clip) }
    }

    private fun surfaces(level: SimplifyLevel): Map<CityMaterial, Geometry> {
        if (!options.surfaces) return emptyMap()
        val clip = CityGeometry.square(extent)
        return data.surfaces.flatMap { area ->
            val material = OsmTags.surface(area.tags)!!
            CityGeometry.polygons(area, projection).map { material to CityGeometry.simplify(it, level) }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, g) -> CityGeometry.union(g).intersection(clip) }
    }

    /** Chunk grid cells overlapping the city box. */
    private fun cells(): List<Pair<Int, Int>> {
        val s = options.chunkSize
        val x0 = floor(extent.minX / s).toInt(); val x1 = floor((extent.maxX - 1e-9) / s).toInt()
        val z0 = floor(extent.minY / s).toInt(); val z1 = floor((extent.maxY - 1e-9) / s).toInt()
        return (z0..z1).flatMap { z -> (x0..x1).map { x -> x to z } }
    }

    private fun cellBounds(x: Int, z: Int): Envelope {
        val s = options.chunkSize
        return Envelope(x * s, (x + 1) * s, z * s, (z + 1) * s).intersection(extent)
    }

    private fun cellOf(polygon: Polygon): Pair<Int, Int> {
        val c = polygon.centroid.coordinate
        return floor(c.x / options.chunkSize).toInt() to floor(c.y / options.chunkSize).toInt()
    }

    /** The generated chunks, empty ones dropped. */
    fun build(): List<CityChunk> {
        val level = options.simplify
        val collisionLevel = if (level.ordinal < SimplifyLevel.MEDIUM.ordinal) SimplifyLevel.MEDIUM else level
        val render = Layers(buildings(level), roads(level), surfaces(level))
        val collide = if (level == collisionLevel) render else Layers(buildings(collisionLevel), roads(collisionLevel), surfaces(collisionLevel))
        val boxSource = buildings(collisionLevel)
        val boxRoads = data.roads.mapNotNull { way -> OsmTags.roadClass(way.tags)?.let { cls -> way to cls } }
            .filter { (_, cls) -> !(collisionLevel.dropMinorRoads && cls.minor) }
        return cells().mapNotNull { (cx, cz) ->
            val bounds = cellBounds(cx, cz)
            if (bounds.isNull || bounds.area <= 0) return@mapNotNull null
            val clip = CityGeometry.square(bounds)
            val parts = meshes(render, cx, cz, clip, offsets = true)
            val collision = if (options.buildingColliders || options.groundColliders) {
                collisionMesh(collide, cx, cz, clip)
            } else null
            val buildingCount = render.buildings.count { cellOf(it.polygon) == cx to cz }
            if (parts.isEmpty()) return@mapNotNull null
            CityChunk(cx, cz, bounds, parts, collision, boxes(boxSource, boxRoads, cx, cz, clip, bounds), buildingCount)
        }
    }

    fun stats(): CityStats {
        val chunks = build()
        return CityStats(
            buildings = chunks.sumOf { it.buildingCount },
            roads = data.roads.size,
            surfaces = data.surfaces.size,
            chunks = chunks.size,
            triangles = chunks.sumOf { it.triangleCount },
            boxes = chunks.sumOf { it.boxes.size },
        )
    }

    private class Layers(val buildings: List<Building>, val roads: Map<CityMaterial, Geometry>, val surfaces: Map<CityMaterial, Geometry>)

    private fun meshes(layers: Layers, cx: Int, cz: Int, clip: Polygon, offsets: Boolean): Map<CityMaterial, GlbMesh> {
        val builders = LinkedHashMap<CityMaterial, MeshBuilder>()
        fun b(m: CityMaterial) = builders.getOrPut(m) { MeshBuilder(m.id) }
        fun y(m: CityMaterial) = if (offsets) CityGeometry.layerOffset(m) else 0.0
        layers.buildings.filter { cellOf(it.polygon) == cx to cz }.forEach {
            CityGeometry.walls(it.polygon, it.base, it.top, b(CityMaterial.WALL))
            CityGeometry.cap(it.polygon, it.top, b(CityMaterial.ROOF))
        }
        (layers.surfaces + layers.roads).forEach { (m, g) ->
            CityGeometry.polygonsOf(g.intersection(clip)).forEach { CityGeometry.cap(it, y(m), b(m)) }
        }
        if (options.ground) CityGeometry.cap(clip, y(CityMaterial.GROUND), b(CityMaterial.GROUND))
        return builders.mapValues { (m, builder) -> builder.build(BASE_COLORS.getValue(m)) }.filterValues { !it.isEmpty }
    }

    private fun collisionMesh(layers: Layers, cx: Int, cz: Int, clip: Polygon): GlbMesh? {
        val out = MeshBuilder("collision")
        if (options.buildingColliders) {
            layers.buildings.filter { cellOf(it.polygon) == cx to cz }.forEach {
                CityGeometry.walls(it.polygon, it.base, it.top, out)
                CityGeometry.cap(it.polygon, it.top, out)
            }
        }
        if (options.groundColliders) {
            // Flat walkable surfaces all lie on the ground plane without offsets; the ground quad covers them.
            if (options.ground) CityGeometry.cap(clip, 0.0, out)
            else (layers.surfaces + layers.roads).values.forEach { g -> CityGeometry.polygonsOf(g.intersection(clip)).forEach { CityGeometry.cap(it, 0.0, out) } }
        }
        return out.build().takeIf { !it.isEmpty }
    }

    private fun boxes(buildings: List<Building>, roads: List<Pair<OsmWay, RoadClass>>, cx: Int, cz: Int, clip: Polygon, bounds: Envelope): List<CityBox> {
        val out = ArrayList<CityBox>()
        if (options.buildingColliders) {
            buildings.filter { cellOf(it.polygon) == cx to cz }.forEachIndexed { i, b ->
                orientedBox("Building_$i", b.polygon, b.base, b.top)?.let(out::add)
            }
        }
        if (options.groundColliders) {
            if (options.roads) {
                var n = 0
                roads.forEach { (way, cls) ->
                    val line = CityGeometry.line(way, projection) ?: return@forEach
                    val simplified = CityGeometry.simplify(line, SimplifyLevel.MEDIUM).intersection(clip)
                    val half = OsmTags.width(way.tags)?.div(2) ?: cls.halfWidth
                    for (k in 0 until simplified.numGeometries) {
                        val c = simplified.getGeometryN(k).coordinates
                        for (s in 0 until c.size - 1) {
                            segmentBox("Road_${n++}", c[s].x, c[s].y, c[s + 1].x, c[s + 1].y, half)?.let(out::add)
                        }
                    }
                }
            }
            if (options.ground) {
                out += CityBox("Ground", doubleArrayOf(bounds.centre().x, -FLAT_HALF_THICKNESS, bounds.centre().y),
                    doubleArrayOf(bounds.width / 2, FLAT_HALF_THICKNESS, bounds.height / 2), 0.0)
            }
        }
        return out
    }

    companion object {
        const val FLAT_HALF_THICKNESS = 0.05

        val BASE_COLORS: Map<CityMaterial, FloatArray> = mapOf(
            CityMaterial.WALL to floatArrayOf(0.78f, 0.75f, 0.70f, 1f),
            CityMaterial.ROOF to floatArrayOf(0.45f, 0.42f, 0.40f, 1f),
            CityMaterial.ROAD to floatArrayOf(0.22f, 0.22f, 0.24f, 1f),
            CityMaterial.PATH to floatArrayOf(0.62f, 0.60f, 0.56f, 1f),
            CityMaterial.GRASS to floatArrayOf(0.33f, 0.55f, 0.27f, 1f),
            CityMaterial.WATER to floatArrayOf(0.20f, 0.42f, 0.65f, 1f),
            CityMaterial.PLAZA to floatArrayOf(0.70f, 0.67f, 0.62f, 1f),
            CityMaterial.GROUND to floatArrayOf(0.50f, 0.50f, 0.48f, 1f),
        )

        /** The minimum-area oriented rectangle of [polygon] as a box from [base] to [top]. */
        fun orientedBox(name: String, polygon: Polygon, base: Double, top: Double): CityBox? {
            val rect = MinimumDiameter.getMinimumRectangle(polygon) as? Polygon ?: return null
            val c = rect.exteriorRing.coordinates
            if (c.size < 4) return null
            val ax = c[1].x - c[0].x; val az = c[1].y - c[0].y
            val bx = c[2].x - c[1].x; val bz = c[2].y - c[1].y
            val la = sqrt(ax * ax + az * az); val lb = sqrt(bx * bx + bz * bz)
            if (la < 1e-6 || lb < 1e-6) return null
            val centre = rect.centroid.coordinate
            val yaw = Math.toDegrees(atan2(-az, ax))
            val h = (top - base) / 2
            return CityBox(name, doubleArrayOf(centre.x, base + h, centre.y), doubleArrayOf(la / 2, h, lb / 2), yaw)
        }

        /** A flat box along the segment (x0,z0)→(x1,z1), [halfWidth] across, centred just below the ground. */
        fun segmentBox(name: String, x0: Double, z0: Double, x1: Double, z1: Double, halfWidth: Double): CityBox? {
            val dx = x1 - x0; val dz = z1 - z0
            val len = sqrt(dx * dx + dz * dz)
            if (len < 0.5) return null
            return CityBox(name, doubleArrayOf((x0 + x1) / 2, -FLAT_HALF_THICKNESS, (z0 + z1) / 2),
                doubleArrayOf(len / 2, FLAT_HALF_THICKNESS, halfWidth), Math.toDegrees(atan2(-dz, dx)))
        }
    }
}
