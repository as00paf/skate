package com.pafoid.skate.engine.assets.procedural

import org.joml.Vector2f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class MeshBuilderTest {

    /** Signed volume by the divergence theorem: equals the solid's volume only when it is closed and wound outward. */
    private fun MeshBuilder.signedVolume(): Float =
        triangles().sumOf { (a, b, c) -> a.dot(Vector3f(b).cross(c)).toDouble() / 6.0 }.toFloat()

    @Test
    fun MeshBuilder_BoxIsClosedAndWoundOutward() {
        val mesh = MeshBuilder().box(Vector3f(-1f, 0f, -0.5f), Vector3f(1f, 0.5f, 0.5f))

        assertEquals(12, mesh.triangleCount)
        assertEquals(1f, mesh.signedVolume(), 1e-4f)
        mesh.triangles().zip(mesh.faceNormals()).forEach { (tri, n) ->
            val centroid = Vector3f(tri.first).add(tri.second).add(tri.third).div(3f)
            assertTrue(n.dot(Vector3f(centroid).sub(0f, 0.25f, 0f)) > 0f, "inward face at $centroid")
        }
    }

    @Test
    fun MeshBuilder_ExtrudedConcaveProfileIsClosedSolid() {
        // Flat-bottomed U: area = 4 * 1 - 2 * 0.5 = 3, width 2 => volume 6.
        val profile = listOf(Vector2f(-2f, 1f), Vector2f(-1f, 1f), Vector2f(-1f, 0.5f), Vector2f(1f, 0.5f), Vector2f(1f, 1f), Vector2f(2f, 1f))

        val mesh = MeshBuilder().extrudeProfile(profile, -1f, 1f)

        assertEquals(6f, mesh.signedVolume(), 1e-3f)
        val (min, max) = mesh.bounds()
        assertEquals(Vector3f(-1f, 0f, -2f), min)
        assertEquals(Vector3f(1f, 1f, 2f), max)
    }

    @Test
    fun MeshBuilder_ExtrudeAlongXSwapsRunAndSide() {
        val mesh = MeshBuilder().extrudeProfile(listOf(Vector2f(-1f, 0.2f), Vector2f(1f, 0.2f)), -0.5f, 0.5f, run = MeshBuilder.RIGHT)

        val (min, max) = mesh.bounds()
        assertEquals(-1f, min.x, 1e-6f); assertEquals(1f, max.x, 1e-6f)
        assertEquals(-0.5f, min.z, 1e-6f); assertEquals(0.5f, max.z, 1e-6f)
        assertEquals(0.4f, mesh.signedVolume(), 1e-4f)
    }

    @Test
    fun MeshBuilder_TubeIsClosedAroundItsAxis() {
        val mesh = MeshBuilder().tube(Vector3f(0f, 1f, -1f), Vector3f(0f, 1f, 1f), 0.1f, sides = 16)

        // Regular 16-gon area * length.
        val polygonArea = 0.5f * 16 * 0.01f * kotlin.math.sin(2 * Math.PI / 16).toFloat()
        assertEquals(polygonArea * 2f, mesh.signedVolume(), 1e-4f)
    }

    @Test
    fun MeshBuilder_RevolvedWallFacesTheAxisAndTheDeckFacesUp() {
        val profile = listOf(Vector2f(1f, 0f), Vector2f(1.5f, 1f), Vector2f(2f, 1f), Vector2f(2f, 0f))

        val mesh = MeshBuilder().revolve(profile, sectors = 12)

        assertEquals(12 * 3 * 2, mesh.triangleCount)
        mesh.triangles().zip(mesh.faceNormals()).forEach { (tri, n) ->
            val c = Vector3f(tri.first).add(tri.second).add(tri.third).div(3f)
            val radial = Vector3f(c.x, 0f, c.z).normalize()
            val r = kotlin.math.sqrt(c.x * c.x + c.z * c.z)
            when {
                abs(c.y - 1f) < 1e-4f -> assertTrue(n.y > 0.99f, "deck not facing up at $c")
                r > 1.9f -> assertTrue(n.dot(radial) > 0.9f, "outer wall not facing out at $c")
                else -> assertTrue(n.dot(radial) < 0f && n.y > 0f, "transition not facing the bowl at $c")
            }
        }
    }

    @Test
    fun MeshBuilder_ObjHasOneVertexUvAndNormalPerCornerAndOneBasedFaces() {
        val mesh = MeshBuilder().box(Vector3f(0f), Vector3f(1f))

        val lines = mesh.toObj("cube").lines()
        assertEquals(24, lines.count { it.startsWith("v ") })
        assertEquals(24, lines.count { it.startsWith("vt ") })
        assertEquals(24, lines.count { it.startsWith("vn ") })
        val faces = lines.filter { it.startsWith("f ") }
        assertEquals(12, faces.size)
        val indices = faces.flatMap { f -> f.removePrefix("f ").split(' ').map { it.substringBefore('/').toInt() } }
        assertEquals(1, indices.min()); assertEquals(24, indices.max())
    }

    @Test
    fun ConvexPieces_ProfileColumnsMergeFlatRunsAndSkipSteps() {
        val steps = listOf(Vector2f(-1f, 0.4f), Vector2f(0f, 0.4f), Vector2f(0f, 0.2f), Vector2f(1f, 0.2f))
        val flat = listOf(Vector2f(-1f, 0.4f), Vector2f(0f, 0.4f), Vector2f(1f, 0.4f))

        val stepPieces = ConvexPieces.profileColumns(steps, -0.5f, 0.5f)
        val flatPieces = ConvexPieces.profileColumns(flat, -0.5f, 0.5f)

        assertEquals(2, stepPieces.size)
        assertEquals(1, flatPieces.size)
        assertTrue(stepPieces.all { it.size == 8 })
        assertEquals(0.4f, stepPieces[0].maxOf { it.y }, 1e-6f)
        assertEquals(0.2f, stepPieces[1].maxOf { it.y }, 1e-6f)
        assertEquals(0f, stepPieces.flatten().minOf { it.y }, 1e-6f)
    }

    @Test
    fun ConvexPieces_RevolvedBandsCoverEverySectorAndSegment() {
        val wall = listOf(Vector2f(1f, 0f), Vector2f(1.2f, 0.3f), Vector2f(1.5f, 1f))

        val bands = ConvexPieces.revolvedBands(wall, outerRadius = 2f, sectors = 8)

        assertEquals(8 * 2, bands.size)
        bands.flatten().forEach { p ->
            val r = kotlin.math.sqrt(p.x * p.x + p.z * p.z)
            assertTrue(r in 0.99f..2.01f && p.y in 0f..1f, "band point out of the ring: $p")
        }
    }

    @Test
    fun ConvexPieces_SegmentBoxSurroundsTheBar() {
        val points = ConvexPieces.segment(Vector3f(0f, 1f, -1f), Vector3f(0f, 0.5f, 1f), 0.05f)

        assertEquals(8, points.size)
        points.forEach { p -> assertTrue(abs(p.x) <= 0.05f + 1e-5f) }
        assertEquals(1f + 0.05f, points.maxOf { it.y }, 0.01f)
    }
}
