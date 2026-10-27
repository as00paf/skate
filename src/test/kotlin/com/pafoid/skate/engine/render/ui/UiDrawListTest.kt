package com.pafoid.skate.engine.render.ui

import com.pafoid.skate.engine.assets.data.Texture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UiDrawListTest {
    private fun tex(w: Int = 30, h: Int = 30) = Texture(width = w, height = h)

    @Test
    fun UiDrawList_ScissorStackIntersectsAndPops() {
        val l = UiDrawList()
        l.pushScissor(0, 0, 100, 100)
        l.pushScissor(50, 50, 100, 100)
        assertEquals(UiRect(50, 50, 50, 50), l.currentScissor)
        l.popScissor()
        assertEquals(UiRect(0, 0, 100, 100), l.currentScissor)
        l.popScissor()
        assertNull(l.currentScissor)
        assertThrows(IllegalStateException::class.java) { l.popScissor() }
    }

    @Test
    fun UiDrawList_BatchesBreakOnTextureOrScissorChange() {
        val l = UiDrawList()
        val a = tex()
        val b = tex()
        l.drawQuad(0f, 0f, 10f, 10f, texture = a)
        l.drawQuad(10f, 0f, 10f, 10f, texture = a)
        l.drawQuad(0f, 0f, 10f, 10f, texture = b)
        l.pushScissor(0, 0, 50, 50)
        l.drawQuad(0f, 0f, 10f, 10f, texture = b)
        assertEquals(listOf(2, 1, 1), l.batches.map { it.quadCount })
        assertEquals(listOf(0, 2, 3), l.batches.map { it.firstQuad })
        assertEquals(UiRect(0, 0, 50, 50), l.batches[2].scissor)
    }

    @Test
    fun UiDrawList_QuadsOutsideScissorOrEmptyAreCulled() {
        val l = UiDrawList()
        l.pushScissor(0, 0, 10, 10)
        l.drawQuad(20f, 20f, 5f, 5f)
        l.drawQuad(0f, 0f, 0f, 5f)
        assertEquals(0, l.quadCount)
        l.drawQuad(5f, 5f, 20f, 20f)
        assertEquals(1, l.quadCount)
    }

    @Test
    fun UiDrawList_QuadVerticesCarryPositionColorUvAndTexId() {
        val l = UiDrawList()
        l.drawQuad(1f, 2f, 3f, 4f, 0.1f, 0.2f, 0.3f, 0.4f, tex(), 0f, 0f, 1f, 1f)
        val v = l.vertexData
        assertEquals(1f, v[0]); assertEquals(2f, v[1])
        assertEquals(0.4f, v[5])
        assertEquals(0f, v[8])
        assertEquals(4f, v[10]); assertEquals(2f, v[11])
        assertEquals(6f, v[21]) // third vertex y = 2 + 4
        val untextured = UiDrawList().also { it.drawQuad(0f, 0f, 1f, 1f) }
        assertEquals(-1f, untextured.vertexData[8])
    }

    @Test
    fun UiDrawList_NineSliceEmitsNineQuadsCoveringPanelWithFixedCorners() {
        val l = UiDrawList()
        l.drawNineSlice(10f, 20f, 100f, 60f, tex(), NineSliceInsets(10, 10, 10, 10))
        assertEquals(9, l.quadCount)
        val v = l.vertexData
        // first quad is the top-left corner: 10x10 at (10,20); uv 0..1/3
        assertEquals(10f, v[0]); assertEquals(20f, v[1])
        assertEquals(20f, v[10]) // right edge x
        assertEquals(1f / 3f, v[16], 1e-6f)
        // last quad's bottom-right vertex reaches the panel corner (110, 80)
        val last = 8 * 4 * 10
        assertEquals(110f, v[last + 20]); assertEquals(80f, v[last + 21])
    }

    @Test
    fun UiDrawList_NineSliceAxisShrinksBordersWhenPanelTooSmall() {
        val a = UiDrawList.nineSliceAxis(10f, 8f, 8f)
        assertArrayEquals(floatArrayOf(0f, 5f, 5f, 10f), a, 1e-6f)
        assertArrayEquals(floatArrayOf(0f, 4f, 16f, 20f), UiDrawList.nineSliceAxis(20f, 4f, 4f), 1e-6f)
    }

    @Test
    fun GridFont_GlyphQuadsUseAtlasCellsAndSkipUnknownChars() {
        val font = GridFont(tex(160, 160), columns = 16, rows = 16, firstChar = 0)
        assertArrayEquals(floatArrayOf(1f / 16, 4f / 16, 2f / 16, 5f / 16), font.uv('A'), 1e-6f) // 65 = row 4, col 1
        val l = UiDrawList()
        val adv = l.drawText(GridFont(tex(), 2, 2, firstChar = 65), "AZB", 0f, 0f, 8f, 8f)
        assertEquals(24f, adv)
        assertEquals(2, l.quadCount)
    }

    @Test
    fun UiPanelSample_ClipsTextToPanelInterior() {
        val l = UiDrawList()
        val font = GridFont(tex(), 16, 16, 0)
        l.drawClippedPanelWithText(tex(), NineSliceInsets(4, 4, 4, 4), font, "HELLO WORLD", 0, 0, 40, 20, 8f, 8f)
        assertEquals(0, l.scissorDepth)
        val textBatches = l.batches.filter { it.scissor != null }
        assertEquals(UiRect(4, 4, 32, 12), textBatches.single().scissor)
    }
}
