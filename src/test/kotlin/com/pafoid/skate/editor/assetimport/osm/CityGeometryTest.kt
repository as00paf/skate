package com.pafoid.skate.editor.assetimport.osm

import com.pafoid.skate.engine.assets.gltf.GlbMesh
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Polygon
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** AST-34 (AST-17 §4.3–4.6, §9): repair, triangulation, extrusion, ribbons, chunks, simplify and box fallback. */
class CityGeometryTest {
    private val f = CityGeometry.factory

    private fun poly(vararg xz: Double, holes: List<DoubleArray> = emptyList()): Polygon {
        fun ring(v: DoubleArray) = f.createLinearRing((v.toList().chunked(2).map { Coordinate(it[0], it[1]) } + Coordinate(v[0], v[1])).toTypedArray())
        return f.createPolygon(ring(xz), holes.map(::ring).toTypedArray())
    }

    /** Area of the triangles' projection on the ground, and the smallest signed up-normal. */
    private fun capStats(mesh: GlbMesh): Pair<Double, Double> {
        var area = 0.0; var minUp = Double.MAX_VALUE
        for (t in 0 until mesh.triangleCount) {
            val (a, b, c) = (0..2).map { mesh.indices[t * 3 + it] * 3 }
            val p = mesh.positions
            val up = (p[b + 2] - p[a + 2]).toDouble() * (p[c] - p[a]) - (p[b] - p[a]).toDouble() * (p[c + 2] - p[a + 2])
            area += abs(up) / 2; minUp = minOf(minUp, up)
        }
        return area to minUp
    }

    @Test
    fun Cap_TrianglesFaceUpAndCoverThePolygonMinusItsHoles() {
        val out = MeshBuilder("cap")
        CityGeometry.cap(poly(0.0, 0.0, 20.0, 0.0, 20.0, 20.0, 0.0, 20.0, holes = listOf(doubleArrayOf(5.0, 5.0, 15.0, 5.0, 15.0, 15.0, 5.0, 15.0))), 3.0, out)
        val mesh = out.build()
        val (area, minUp) = capStats(mesh)
        assertEquals(300.0, area, 1e-6)
        assertTrue(minUp > 0, "no inverted triangles")
        assertTrue((0 until mesh.vertexCount).all { mesh.positions[it * 3 + 1] == 3f && mesh.normals[it * 3 + 1] == 1f })
    }

    @Test
    fun Walls_FaceOutwardForShellsAndHoles() {
        val out = MeshBuilder("walls")
        val square = poly(0.0, 0.0, 0.0, 10.0, 10.0, 10.0, 10.0, 0.0, holes = listOf(doubleArrayOf(4.0, 4.0, 6.0, 4.0, 6.0, 6.0, 4.0, 6.0)))
        CityGeometry.walls(square, 0.0, 9.0, out)
        val mesh = out.build()
        assertEquals((4 + 4) * 2, mesh.triangleCount, "two triangles per edge, shell and hole")
        for (v in 0 until mesh.vertexCount) {
            val x = mesh.positions[v * 3]; val z = mesh.positions[v * 3 + 2]
            val nx = mesh.normals[v * 3]; val nz = mesh.normals[v * 3 + 2]
            val onShell = x == 0f || x == 10f || z == 0f || z == 10f
            // Shell walls point away from the centre; hole walls point into the hole (towards the centre).
            val dot = (x - 5f) * nx + (z - 5f) * nz
            if (onShell) assertTrue(dot > 0, "shell normal at $x,$z") else assertTrue(dot < 0, "hole normal at $x,$z")
        }
        // Triangle winding agrees with the stored normal (counter-clockwise front faces).
        for (t in 0 until mesh.triangleCount) {
            val (a, b, c) = (0..2).map { mesh.indices[t * 3 + it] * 3 }
            val p = mesh.positions
            val ux = p[b] - p[a]; val uy = p[b + 1] - p[a + 1]; val uz = p[b + 2] - p[a + 2]
            val vx = p[c] - p[a]; val vy = p[c + 1] - p[a + 1]; val vz = p[c + 2] - p[a + 2]
            val cx = uy * vz - uz * vy; val cz = ux * vy - uy * vx
            assertTrue(cx * mesh.normals[a] + cz * mesh.normals[a + 2] > 0)
        }
    }

    @Test
    fun Polygons_RepairSelfTouchingRingsWithDuplicateNodes() {
        val data = OsmParser.parse(OsmDataTest.fixture())
        val projection = LocalProjection(OsmDataTest.CENTER)
        val polygons = CityGeometry.polygons(data.areas.single { it.id == 4L }, projection)
        assertTrue(polygons.isNotEmpty() && polygons.all { it.isValid })
        val area = polygons.sumOf { it.area }
        val out = MeshBuilder("roof")
        polygons.forEach { CityGeometry.cap(it, 9.0, out) }
        val (capArea, minUp) = capStats(out.build())
        assertEquals(area, capArea, 1e-3, "triangulation preserves the repaired area")
        assertTrue(minUp > 0)
        val courtyard = CityGeometry.polygons(data.areas.single { it.id == 3L }, projection).single()
        assertEquals(1600.0 - 400.0, courtyard.area, 1.0, "multipolygon hole kept")
    }

    @Test
    fun Ribbon_HasTheClassWidthWithFlatCaps() {
        val line = f.createLineString(arrayOf(Coordinate(0.0, 0.0), Coordinate(100.0, 0.0)))
        val ribbon = CityGeometry.ribbon(line, RoadClass.RESIDENTIAL.halfWidth)
        assertEquals(100.0 * 6.0, ribbon.area, 1e-6)
        assertEquals(0.0, ribbon.envelopeInternal.minX, 1e-9, "flat cap: no overhang")
    }

    private fun build(options: CityOptions = CityOptions()) = CityBuilder(OsmParser.parse(OsmDataTest.fixture()), OsmDataTest.BOX, options).build()

    @Test
    fun Builder_SplitsIntoChunksAndClipsSurfacesAtTheirBorders() {
        val chunks = build()
        assertEquals(setOf(-1 to -1, 0 to -1, -1 to 0, 0 to 0), chunks.map { it.x to it.z }.toSet(), "400 m box over a 256 m grid at the centre")
        for (chunk in chunks) {
            for (m in listOf(CityMaterial.ROAD, CityMaterial.PATH, CityMaterial.GRASS, CityMaterial.WATER, CityMaterial.GROUND)) {
                val mesh = chunk.parts[m] ?: continue
                for (v in 0 until mesh.vertexCount) {
                    val x = mesh.positions[v * 3].toDouble(); val z = mesh.positions[v * 3 + 2].toDouble()
                    assertTrue(x >= chunk.bounds.minX - 1e-3 && x <= chunk.bounds.maxX + 1e-3 && z >= chunk.bounds.minY - 1e-3 && z <= chunk.bounds.maxY + 1e-3,
                        "${chunk.name} $m vertex $x,$z outside ${chunk.bounds}")
                }
            }
            assertTrue(CityMaterial.GROUND in chunk.parts, chunk.name)
        }
        // The east-west road crosses the z = 0 chunks, the north-south one the x = -1 chunks: clipped into three.
        assertEquals(setOf(-1 to -1, -1 to 0, 0 to 0), chunks.filter { CityMaterial.ROAD in it.parts }.map { it.x to it.z }.toSet())
        val tall = chunks.single { it.x == 0 && it.z == 0 }.parts.getValue(CityMaterial.ROOF)
        assertTrue((0 until tall.vertexCount).any { tall.positions[it * 3 + 1] == 12f }, "building 1 extruded to its height tag")
        val road = chunks.flatMap { listOfNotNull(it.parts[CityMaterial.ROAD]) }
        assertTrue(road.all { m -> (0 until m.vertexCount).all { m.positions[it * 3 + 1] == CityGeometry.layerOffset(CityMaterial.ROAD).toFloat() } })
    }

    @Test
    fun Builder_SimplifyLevelsDropSmallFeatures() {
        val none = build(CityOptions(simplify = SimplifyLevel.NONE))
        val medium = build(CityOptions(simplify = SimplifyLevel.MEDIUM))
        val strong = build(CityOptions(simplify = SimplifyLevel.STRONG))
        assertEquals(5, none.sumOf { it.buildingCount }, "every building, the bow-tie repaired into one or more parts")
        assertEquals(4, medium.sumOf { it.buildingCount }, "Medium drops the 9 m² shed")
        assertTrue(none.sumOf { it.triangleCount } > medium.sumOf { it.triangleCount })
        assertTrue(medium.sumOf { it.triangleCount } >= strong.sumOf { it.triangleCount })
        val paths = { c: List<CityChunk> -> c.sumOf { it.parts[CityMaterial.PATH]?.triangleCount ?: 0 } }
        assertTrue(paths(strong) == 0, "Strong drops footways")
        assertTrue(paths(medium) > 0)
    }

    @Test
    fun Builder_IncludeTogglesAndCollisionMesh() {
        val bare = build(CityOptions(buildings = false, roads = false, surfaces = false))
        assertTrue(bare.all { it.parts.keys == setOf(CityMaterial.GROUND) })
        val noColliders = build(CityOptions(buildingColliders = false, groundColliders = false))
        assertTrue(noColliders.all { it.collision == null && it.boxes.isEmpty() })
        val chunk = build().single { it.x == 0 && it.z == 0 }
        val collision = chunk.collision!!
        assertTrue((0 until collision.vertexCount).all { collision.positions[it * 3 + 1] == 0f || collision.positions[it * 3 + 1] >= 0f })
        assertTrue((0 until collision.vertexCount).none { collision.positions[it * 3 + 1] == CityGeometry.layerOffset(CityMaterial.ROAD).toFloat() },
            "no layer offsets in the collision mesh")
    }

    @Test
    fun Boxes_OneOrientedBoxPerBuildingAndFlatBoxesForRoadsAndGround() {
        val chunk = build().single { it.x == 0 && it.z == 0 }
        val building = chunk.boxes.filter { it.name.startsWith("Building_") }
        assertEquals(chunk.buildingCount, building.size)
        val b1 = building.minByOrNull { abs(it.center[0] - 50) + abs(it.center[2] - 50) }!!
        assertEquals(6.0, b1.center[1], 1e-6); assertEquals(6.0, b1.halfExtents[1], 1e-6)
        assertEquals(10.0, b1.halfExtents[0], 0.01); assertEquals(10.0, b1.halfExtents[2], 0.01)
        assertTrue(chunk.boxes.count { it.name.startsWith("Road_") } >= 2)
        val ground = chunk.boxes.single { it.name == "Ground" }
        assertEquals(0.0, ground.center[1] + ground.halfExtents[1], 1e-9, "ground box top at y = 0")
    }

    @Test
    fun Boxes_FollowARotatedFootprint() {
        val a = Math.toRadians(30.0)
        fun corner(x: Double, z: Double) = doubleArrayOf(x * cos(a) + z * sin(a), -x * sin(a) + z * cos(a))
        val pts = listOf(corner(-10.0, -4.0), corner(10.0, -4.0), corner(10.0, 4.0), corner(-10.0, 4.0)).flatMap { it.toList() }
        val box = CityBuilder.orientedBox("B", poly(*pts.toDoubleArray()), 0.0, 6.0)!!
        val extents = listOf(box.halfExtents[0], box.halfExtents[2]).sorted()
        assertEquals(4.0, extents[0], 1e-6); assertEquals(10.0, extents[1], 1e-6)
        // The yaw maps local +X onto one of the rectangle's sides: 30° modulo 90°.
        val r = ((box.yawDegrees % 90.0) + 90.0) % 90.0
        assertEquals(30.0, r, 1e-6)
        val seg = CityBuilder.segmentBox("R", 0.0, 0.0, 0.0, -10.0, 3.0)!!
        assertEquals(90.0, seg.yawDegrees, 1e-9, "a north-going segment (−Z) is +X yawed by 90°")
        assertFalse(CityBuilder.segmentBox("R", 0.0, 0.0, 0.1, 0.0, 3.0) != null, "tiny segments get no box")
    }
}
