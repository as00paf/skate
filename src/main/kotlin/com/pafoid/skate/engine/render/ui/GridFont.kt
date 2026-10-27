package com.pafoid.skate.engine.render.ui

import com.pafoid.skate.engine.assets.data.Texture

/** Fixed-cell bitmap font laid out row-major in an atlas, starting at [firstChar]. Minimal path until SDF fonts. */
class GridFont(
    val texture: Texture,
    val columns: Int,
    val rows: Int,
    val firstChar: Int = 32,
    /** Glyph cell width divided by height, used to derive advance from a font size. */
    val cellAspect: Float = 0.5f,
) : UiFont {
    override fun measure(text: String, size: Float): Float = text.length * size * cellAspect

    override fun draw(list: UiDrawList, text: String, x: Float, y: Float, size: Float, r: Float, g: Float, b: Float, a: Float): Float =
        list.drawText(this, text, x, y, size * cellAspect, size, r, g, b, a)

    fun hasGlyph(c: Char): Boolean = c.code - firstChar in 0 until columns * rows

    /** UV rect (u0, v0, u1, v1) of [c], or null when the font does not contain it. */
    fun uv(c: Char): FloatArray? {
        val i = c.code - firstChar
        if (i !in 0 until columns * rows) return null
        val col = i % columns
        val row = i / columns
        return floatArrayOf(col / columns.toFloat(), row / rows.toFloat(), (col + 1) / columns.toFloat(), (row + 1) / rows.toFloat())
    }
}

/** Submits one glyph quad per character; returns the advance (width) used. Unknown characters still advance. */
fun UiDrawList.drawText(
    font: GridFont, text: String, x: Float, y: Float, cellW: Float, cellH: Float,
    r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f,
): Float {
    var cx = x
    for (c in text) {
        font.uv(c)?.let { drawGlyph(font.texture, cx, y, cellW, cellH, it[0], it[1], it[2], it[3], r, g, b, a) }
        cx += cellW
    }
    return cx - x
}
