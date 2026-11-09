package com.pafoid.skate.engine.physics3d

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShapeWireframeTest {

    private fun segments(lines: FloatArray) = lines.size / 6

    @Test
    fun hullEdges_Cube_HasTwelveEdgesAndNoDiagonals() {
        val cube = listOf(-1f, 1f).flatMap { x -> listOf(-1f, 1f).flatMap { y -> listOf(-1f, 1f).flatMap { z -> listOf(x, y, z) } } }

        assertEquals(12, segments(ShapeWireframe.hullEdges(cube.toFloatArray())))
    }

    @Test
    fun hullEdges_Wedge_HasNineEdges_InteriorPointIgnored() {
        val wedge = floatArrayOf(
            0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f,
            0f, 0f, 2f, 1f, 0f, 2f, 0f, 1f, 2f,
            0.2f, 0.2f, 1f, // inside
        )

        assertEquals(9, segments(ShapeWireframe.hullEdges(wedge)))
    }

    @Test
    fun featureEdges_FlatQuad_DropsTheDiagonal_FoldedQuadKeepsTheCrease() {
        val flat = floatArrayOf(0f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 0f, 1f)
        assertEquals(4, segments(ShapeWireframe.featureEdges(flat)))

        val folded = floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)
        assertEquals(5, segments(ShapeWireframe.featureEdges(folded)))
    }
}
