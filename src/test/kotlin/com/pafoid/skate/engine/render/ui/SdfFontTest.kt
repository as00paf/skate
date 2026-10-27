package com.pafoid.skate.engine.render.ui

import com.pafoid.skate.engine.assets.data.Texture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Font

class SdfFontTest {

    @Test
    fun SdfGenerator_DistanceIsHalfOnEdgeAndMonotonicAcrossIt() {
        val w = 20
        val cov = ByteArray(w * 5) { i -> if (i % w >= 10) -1 else 0 }
        val f = SdfGenerator.distanceField(cov, w, 5, 6)
        fun at(x: Int) = f[2 * w + x].toInt() and 0xFF
        assertTrue(at(9) < 128 && at(10) >= 128)
        assertEquals(0, at(0))
        assertEquals(255, at(19))
        for (x in 1 until w) assertTrue(at(x) >= at(x - 1))
    }

    @Test
    fun SdfGenerator_GeneratesGlyphsWithMetricsAndInsideOutsideDistances() {
        val data = SdfGenerator.generate(Font(Font.SANS_SERIF, Font.BOLD, 1), intArrayOf('I'.code, ' '.code, 'é'.code), basePx = 32, spread = 4, atlasWidth = 256)
        val i = data.glyphs.getValue('I'.code)
        assertTrue(i.advance > 0f)
        val values = (0 until i.h).flatMap { y -> (0 until i.w).map { x -> data.distances[(i.y + y) * data.width + i.x + x].toInt() and 0xFF } }
        assertTrue(values.any { it > 200 }, "glyph interior")
        assertTrue(values.any { it < 60 }, "outside")
        assertEquals(i.h, data.glyphs.getValue(' '.code).h)
    }

    @Test
    fun SdfFont_MeasureScalesLinearlyWithSizeAndDrawEmitsSdfQuads() {
        val data = SdfFontData(
            64, 32, ByteArray(64 * 32),
            mapOf('a'.code to SdfGlyph(0, 0, 20, 30, 10f), '?'.code to SdfGlyph(20, 0, 20, 30, 12f)),
            lineHeight = 22f, ascent = 18f, spread = 4,
        )
        val font = SdfFont(Texture(64, 32, 1, 4), data)
        assertEquals(20f, font.measure("aa", 22f), 0.001f)
        assertEquals(80f, font.measure("aa", 88f), 0.001f)
        assertEquals(font.measure("a?", 22f), font.measure("az", 22f), 0.001f, "unknown chars use '?'")
        val list = UiDrawList()
        font.draw(list, "aa", 0f, 0f, 44f)
        assertEquals(2, list.quadCount)
        val stride = com.pafoid.skate.engine.utils.RenderConsts2D.VERTEX_SIZE
        assertEquals(UiDrawList.SDF_MODE, list.vertexData[9])
        assertEquals(-8f, list.vertexData[0], 0.001f) // pen minus scaled spread
        assertEquals(20f - 8f, list.vertexData[4 * stride], 0.001f)
    }
}
