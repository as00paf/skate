package com.pafoid.skate.engine.render.water

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-29 §6.1: chunked water grids with 3 LODs and skirts. */
class WaterMeshBuilderTest {

    @Test
    fun Grid_TwentyMetrePoolAtHalfMetre_IsTwoByTwoChunks() {
        val grid = WaterMeshBuilder.build(20f, 20f, 0.5f)
        assertEquals(4, grid.chunks.size)
        assertEquals(listOf(32, 8, 32, 8), grid.chunks.map { it.quadsX })
        assertEquals(listOf(32, 32, 8, 8), grid.chunks.map { it.quadsZ })
        val first = grid.chunks[0]
        assertEquals(-10f, first.minX); assertEquals(-10f, first.minZ)
        assertEquals(6f, first.maxX); assertEquals(6f, first.maxZ)
        assertEquals(10f, grid.chunks[3].maxX); assertEquals(10f, grid.chunks[3].maxZ)
    }

    @Test
    fun Grid_NonMultipleSize_EndsExactlyOnTheEdge() {
        val grid = WaterMeshBuilder.build(10f, 6f, 0.7f)
        val c = grid.chunks.single()
        assertEquals(15, c.quadsX) // ceil(10 / 0.7)
        assertEquals(5f, c.maxX); assertEquals(3f, c.maxZ)
        val xs = (0..c.quadsX).map { c.vertices[it * 3] }
        assertTrue(xs.zipWithNext().all { (a, b) -> b > a }, "strictly increasing: $xs")
    }

    @Test
    fun Chunks_ShareTheirBorderVertexPositions() {
        val grid = WaterMeshBuilder.build(20f, 4f, 0.5f)
        val (left, right) = grid.chunks
        val leftEdge = (0..left.quadsZ).map { j -> left.vertices[(j * (left.quadsX + 1) + left.quadsX) * 3] to left.vertices[(j * (left.quadsX + 1) + left.quadsX) * 3 + 2] }
        val rightEdge = (0..right.quadsZ).map { j -> right.vertices[(j * (right.quadsX + 1)) * 3] to right.vertices[(j * (right.quadsX + 1)) * 3 + 2] }
        assertEquals(leftEdge, rightEdge)
    }

    @Test
    fun Skirts_HangFromEveryBorderVertex() {
        val c = WaterMeshBuilder.build(4f, 3f, 1f).chunks.single()
        val skirtCount = 2 * (c.quadsX + 1) + 2 * (c.quadsZ + 1)
        assertEquals(c.gridVertexCount + skirtCount, c.vertexCount)
        val ys = (0 until c.vertexCount).map { c.vertices[it * 3 + 1] }
        assertEquals(c.gridVertexCount, ys.count { it == 0f })
        assertEquals(skirtCount, ys.count { it == -1f })
    }

    @Test
    fun Lods_StepOneTwoFour_IncludeSkirtsAndStayInRange() {
        val c = WaterMeshBuilder.build(16f, 16f, 0.5f).chunks.single() // 32 x 32 quads
        val expectedQuads = listOf(32 * 32, 16 * 16, 8 * 8)
        val expectedSkirts = listOf(4 * 32, 4 * 16, 4 * 8)
        for (lod in 0..2) {
            val idx = c.lodIndices[lod]
            assertEquals((expectedQuads[lod] + expectedSkirts[lod]) * 6, idx.size, "lod $lod")
            assertTrue(idx.all { it in 0 until c.vertexCount })
        }
    }

    @Test
    fun Triangles_FaceUpOnTheSurface() {
        val c = WaterMeshBuilder.build(2f, 2f, 1f).chunks.single()
        val idx = c.lodIndices[0]
        val v = c.vertices
        for (t in 0 until 2 * c.quadsX * c.quadsZ) {
            val (a, b, d) = Triple(idx[t * 3], idx[t * 3 + 1], idx[t * 3 + 2])
            val e1x = v[b * 3] - v[a * 3]; val e1z = v[b * 3 + 2] - v[a * 3 + 2]
            val e2x = v[d * 3] - v[a * 3]; val e2z = v[d * 3 + 2] - v[a * 3 + 2]
            assertTrue(e1z * e2x - e1x * e2z > 0f, "triangle $t normal points up")
        }
    }

    @Test
    fun Samples_AlwaysEndOnTheLastLine() {
        assertArrayEquals(intArrayOf(0, 4, 8), WaterMeshBuilder.samples(8, 4))
        assertArrayEquals(intArrayOf(0, 4, 8, 10), WaterMeshBuilder.samples(10, 4))
        assertArrayEquals(intArrayOf(0, 1), WaterMeshBuilder.samples(1, 2))
    }
}
