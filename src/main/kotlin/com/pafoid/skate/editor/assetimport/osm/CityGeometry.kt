package com.pafoid.skate.editor.assetimport.osm

import com.pafoid.skate.engine.assets.gltf.GlbMesh
import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.util.GeometryFixer
import org.locationtech.jts.operation.buffer.BufferParameters
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.simplify.TopologyPreservingSimplifier
import org.locationtech.jts.triangulate.polygon.ConstrainedDelaunayTriangulator
import kotlin.math.sqrt

/** Mesh-count reduction (AST-17 §4.5, decision 3). */
enum class SimplifyLevel(val tolerance: Double, val minBuildingArea: Double, val minPathLength: Double, val minBuildingHeight: Double, val dropMinorRoads: Boolean) {
    NONE(0.0, 0.0, 0.0, 0.0, false),
    MEDIUM(0.5, 15.0, 5.0, 0.0, false),
    STRONG(1.5, 40.0, 5.0, 4.0, true),
}

/** Growable float/int arrays that become a [GlbMesh]. */
class MeshBuilder(private val name: String) {
    private var p = FloatArray(1024); private var n = FloatArray(1024); private var t = FloatArray(1024)
    private var i = IntArray(1024)
    private var pv = 0; private var tv = 0; private var iv = 0
    val vertexCount: Int get() = pv / 3

    fun vertex(x: Double, y: Double, z: Double, nx: Float, ny: Float, nz: Float, u: Double, v: Double): Int {
        if (pv + 3 > p.size) { p = p.copyOf(p.size * 2); n = n.copyOf(n.size * 2) }
        if (tv + 2 > t.size) t = t.copyOf(t.size * 2)
        p[pv] = x.toFloat(); p[pv + 1] = y.toFloat(); p[pv + 2] = z.toFloat()
        n[pv] = nx; n[pv + 1] = ny; n[pv + 2] = nz
        t[tv] = u.toFloat(); t[tv + 1] = v.toFloat()
        pv += 3; tv += 2
        return pv / 3 - 1
    }

    fun triangle(a: Int, b: Int, c: Int) {
        if (iv + 3 > i.size) i = i.copyOf(i.size * 2)
        i[iv] = a; i[iv + 1] = b; i[iv + 2] = c
        iv += 3
    }

    fun build(baseColor: FloatArray = floatArrayOf(1f, 1f, 1f, 1f)): GlbMesh =
        GlbMesh(name, p.copyOf(pv), n.copyOf(pv), t.copyOf(tv), i.copyOf(iv), baseColor)
}

/**
 * Pure geometry for the city generator (AST-17 §4.3), in local metres: JTS `x` is world X and JTS `y` is world Z.
 * OSM polygons are repaired ([GeometryFixer]) before triangulation, so self-touching rings and duplicate nodes are safe.
 */
object CityGeometry {
    val factory = GeometryFactory()

    /** Vertical offsets that keep layered flat surfaces from z-fighting (road above area above ground). */
    fun layerOffset(material: CityMaterial): Double = when (material) {
        CityMaterial.GROUND -> 0.0
        CityMaterial.GRASS, CityMaterial.WATER, CityMaterial.PLAZA -> 0.02
        CityMaterial.PATH -> 0.04
        CityMaterial.ROAD -> 0.06
        else -> 0.0
    }

    fun ring(points: List<GeoPoint>, projection: LocalProjection): LinearRing? {
        val coords = points.map { Coordinate(projection.x(it.lon), projection.z(it.lat)) }.toMutableList()
        if (coords.size < 3) return null
        if (coords.first() != coords.last()) coords += Coordinate(coords.first())
        if (coords.size < 4) return null
        return factory.createLinearRing(coords.toTypedArray())
    }

    /** The repaired polygons of [area] (a multipolygon may give several); empty when nothing valid remains. */
    fun polygons(area: OsmArea, projection: LocalProjection): List<Polygon> {
        val holes = area.holes.mapNotNull { ring(it, projection) }.toTypedArray()
        val parts = area.outer.mapNotNull { ring(it, projection) }.map { shell ->
            factory.createPolygon(shell, holes.filter { shell.envelopeInternal.contains(it.envelopeInternal) }.toTypedArray())
        }
        return parts.flatMap { polygonsOf(GeometryFixer.fix(it)) }
    }

    fun line(way: OsmWay, projection: LocalProjection): LineString? {
        val coords = way.points.map { Coordinate(projection.x(it.lon), projection.z(it.lat)) }
            .fold(ArrayList<Coordinate>()) { acc, c -> if (acc.isEmpty() || acc.last().distance(c) > 1e-6) acc += c; acc }
        return if (coords.size >= 2) factory.createLineString(coords.toTypedArray()) else null
    }

    fun polygonsOf(geometry: Geometry): List<Polygon> =
        (0 until geometry.numGeometries).map(geometry::getGeometryN).flatMap {
            when (it) {
                is Polygon -> if (it.isEmpty || it.area <= 1e-6) emptyList() else listOf(it)
                else -> if (it.numGeometries > 1) polygonsOf(it) else emptyList()
            }
        }

    fun simplify(geometry: Geometry, level: SimplifyLevel): Geometry =
        if (level.tolerance <= 0.0) geometry else TopologyPreservingSimplifier.simplify(geometry, level.tolerance)

    /** Road ribbon: the line buffered with flat caps and mitre joins (mitre limit 2). */
    fun ribbon(line: LineString, halfWidth: Double): Geometry {
        val params = BufferParameters().apply {
            endCapStyle = BufferParameters.CAP_FLAT
            joinStyle = BufferParameters.JOIN_MITRE
            mitreLimit = 2.0
        }
        return org.locationtech.jts.operation.buffer.BufferOp.bufferOp(line, halfWidth, params)
    }

    fun union(geometries: List<Geometry>): Geometry =
        if (geometries.isEmpty()) factory.createGeometryCollection() else UnaryUnionOp.union(geometries)

    fun square(envelope: Envelope): Polygon = factory.toGeometry(envelope) as Polygon

    /** Up-facing triangles of [polygon] at height [y]; UVs are world X/Z in metres. */
    fun cap(polygon: Polygon, y: Double, out: MeshBuilder) {
        val triangles = runCatching { ConstrainedDelaunayTriangulator.triangulate(polygon) }.getOrNull() ?: return
        for (k in 0 until triangles.numGeometries) {
            val c = triangles.getGeometryN(k).coordinates
            if (c.size < 3) continue
            var (a, b, d) = Triple(c[0], c[1], c[2])
            // Normal +Y requires (b-a)×(d-a) to point up: in X/Z that is (bz-az)(dx-ax) - (bx-ax)(dz-az) > 0.
            val cross = (b.y - a.y) * (d.x - a.x) - (b.x - a.x) * (d.y - a.y)
            if (kotlin.math.abs(cross) < 1e-9) continue
            if (cross < 0) { val tmp = b; b = d; d = tmp }
            val ia = out.vertex(a.x, y, a.y, 0f, 1f, 0f, a.x, a.y)
            val ib = out.vertex(b.x, y, b.y, 0f, 1f, 0f, b.x, b.y)
            val id = out.vertex(d.x, y, d.y, 0f, 1f, 0f, d.x, d.y)
            out.triangle(ia, ib, id)
        }
    }

    /**
     * Outward-facing wall quads of [polygon] from [base] to [top]. After `normalize()` JTS shells are clockwise and
     * holes counter-clockwise, so the interior is always on the right of each edge and outward is `(-dz, dx)`.
     */
    fun walls(polygon: Polygon, base: Double, top: Double, out: MeshBuilder) {
        if (top - base <= 0.01) return
        val p = polygon.copy() as Polygon
        p.normalize()
        val rings = listOf(p.exteriorRing) + (0 until p.numInteriorRing).map(p::getInteriorRingN)
        for (ring in rings) {
            val c = ring.coordinates
            var u = 0.0
            for (k in 0 until c.size - 1) {
                val a = c[k]; val b = c[k + 1]
                val dx = b.x - a.x; val dz = b.y - a.y
                val len = sqrt(dx * dx + dz * dz)
                if (len < 1e-6) continue
                val nx = (-dz / len).toFloat(); val nz = (dx / len).toFloat()
                val a0 = out.vertex(a.x, base, a.y, nx, 0f, nz, u, base)
                val b0 = out.vertex(b.x, base, b.y, nx, 0f, nz, u + len, base)
                val b1 = out.vertex(b.x, top, b.y, nx, 0f, nz, u + len, top)
                val a1 = out.vertex(a.x, top, a.y, nx, 0f, nz, u, top)
                out.triangle(a0, b0, b1)
                out.triangle(a0, b1, a1)
                u += len
            }
        }
    }

    /** True when [polygon] winds clockwise in JTS (x, y) — exposed for tests. */
    fun isClockwise(polygon: Polygon): Boolean = !Orientation.isCCW(polygon.exteriorRing.coordinateSequence)
}
