package com.pafoid.skate.engine.render.ui

import com.pafoid.skate.engine.assets.data.Texture
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*

/**
 * One glyph of an SDF atlas. [x],[y],[w],[h] is the atlas cell in texels (including the distance-field spread);
 * [advance] is the pen advance in base pixels; the cell's top-left sits at the pen position minus the spread.
 */
class SdfGlyph(val x: Int, val y: Int, val w: Int, val h: Int, val advance: Float)

/** CPU-side result of SDF font generation: an 8-bit distance atlas (128 = outline) and glyph metrics. */
class SdfFontData(
    val width: Int,
    val height: Int,
    /** One byte per texel: distance mapped so 0.5 is the glyph edge. */
    val distances: ByteArray,
    val glyphs: Map<Int, SdfGlyph>,
    /** Font ascent + descent in base pixels (the unscaled line height). */
    val lineHeight: Float,
    val ascent: Float,
    val spread: Int,
)

/** Signed-distance-field generation; pure, no GL. */
object SdfGenerator {
    /**
     * Converts an 8-bit coverage image to a distance field. Output 128 lies on the outline, 255 is [spread] texels
     * or more inside, 0 is [spread] texels or more outside.
     */
    fun distanceField(coverage: ByteArray, w: Int, h: Int, spread: Int): ByteArray {
        val out = ByteArray(w * h)
        val maxSq = (spread * spread).toFloat()
        for (y in 0 until h) for (x in 0 until w) {
            val inside = (coverage[y * w + x].toInt() and 0xFF) >= 128
            var best = maxSq
            for (dy in -spread..spread) {
                val yy = y + dy
                if (yy !in 0 until h) continue
                for (dx in -spread..spread) {
                    val d = (dx * dx + dy * dy).toFloat()
                    if (d >= best) continue
                    val xx = x + dx
                    if (xx !in 0 until w) continue
                    if (((coverage[yy * w + xx].toInt() and 0xFF) >= 128) != inside) best = d
                }
            }
            val dist = kotlin.math.sqrt(best)
            val signed = if (inside) dist else -dist
            out[y * w + x] = (128f + signed / spread * 128f).toInt().coerceIn(0, 255).toByte()
        }
        return out
    }

    /** Rasterizes [chars] with Java2D at [basePx] and packs their distance fields into one atlas. */
    fun generate(font: Font, chars: IntArray, basePx: Int = 48, spread: Int = 8, atlasWidth: Int = 1024): SdfFontData {
        System.setProperty("java.awt.headless", "true")
        val scaled = font.deriveFont(basePx.toFloat())
        val probe = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY).createGraphics()
        probe.font = scaled
        val metrics = probe.fontMetrics
        val ascent = metrics.ascent
        val lineHeight = ascent + metrics.descent
        val frc = probe.fontRenderContext
        probe.dispose()

        val cellH = lineHeight + 2 * spread
        class Pending(val code: Int, val cellW: Int, val advance: Float)
        val pending = chars.distinct().filter { scaled.canDisplay(it) }.map {
            val adv = scaled.getStringBounds(String(Character.toChars(it)), frc).width.toFloat()
            Pending(it, kotlin.math.ceil(adv).toInt() + 2 * spread, adv)
        }

        val glyphs = HashMap<Int, SdfGlyph>()
        var penX = 0
        var penY = 0
        for (p in pending) {
            if (penX + p.cellW > atlasWidth) { penX = 0; penY += cellH }
            glyphs[p.code] = SdfGlyph(penX, penY, p.cellW, cellH, p.advance)
            penX += p.cellW
        }
        val atlasHeight = penY + cellH

        val image = BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_BYTE_GRAY)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.font = scaled
        g.color = Color.WHITE
        for (p in pending) {
            val glyph = glyphs.getValue(p.code)
            g.drawString(String(Character.toChars(p.code)), (glyph.x + spread).toFloat(), (glyph.y + spread + ascent).toFloat())
        }
        g.dispose()
        val coverage = ByteArray(atlasWidth * atlasHeight)
        image.raster.getDataElements(0, 0, atlasWidth, atlasHeight, coverage)

        // Fields are computed per cell so neighbouring glyphs never bleed into each other.
        val distances = ByteArray(atlasWidth * atlasHeight)
        for (glyph in glyphs.values) {
            val cell = ByteArray(glyph.w * glyph.h)
            for (yy in 0 until glyph.h) System.arraycopy(coverage, (glyph.y + yy) * atlasWidth + glyph.x, cell, yy * glyph.w, glyph.w)
            val field = distanceField(cell, glyph.w, glyph.h, spread)
            for (yy in 0 until glyph.h) System.arraycopy(field, yy * glyph.w, distances, (glyph.y + yy) * atlasWidth + glyph.x, glyph.w)
        }
        return SdfFontData(atlasWidth, atlasHeight, distances, glyphs, lineHeight.toFloat(), ascent.toFloat(), spread)
    }

    /** Printable Latin ranges (ASCII, Latin-1, Latin Extended-A) plus common punctuation. */
    val LATIN: IntArray = (32..126).toList().plus(160..255).plus(256..383).plus(listOf(0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2026, 0x20AC)).toIntArray()
}

/** Proportional font rendered from a signed-distance atlas: text stays crisp at any size (see the batch shader). */
class SdfFont(val texture: Texture, private val data: SdfFontData) : UiFont {
    private val fallback = data.glyphs['?'.code]

    fun destroy() {
        if (texture.texId != 0) glDeleteTextures(texture.texId)
        texture.texId = 0
    }

    companion object {
        /**
         * Default UI font: proportional SDF atlas (Latin, Latin-1, Latin Extended-A) generated once with Java2D;
         * pass a TTF/OTF [file] for a custom typeface. Must run on the GL thread.
         */
        fun create(file: File? = null): SdfFont {
            val awt = if (file != null) Font.createFont(Font.TRUETYPE_FONT, file) else Font(Font.SANS_SERIF, Font.BOLD, 1)
            val data = SdfGenerator.generate(awt, SdfGenerator.LATIN)
            return SdfFont(upload(data), data)
        }

        private fun upload(data: SdfFontData): Texture {
            val buffer = BufferUtils.createByteBuffer(data.width * data.height * 4)
            for (d in data.distances) buffer.put(-1).put(-1).put(-1).put(d)
            buffer.flip()
            val texture = Texture(data.width, data.height, 1, 4, false, null)
            texture.texId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, texture.texId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, data.width, data.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
            return texture
        }
    }

    private fun glyph(c: Char): SdfGlyph? = data.glyphs[c.code] ?: fallback

    override fun measure(text: String, size: Float): Float {
        val scale = size / data.lineHeight
        var w = 0f
        for (c in text) glyph(c)?.let { w += it.advance * scale }
        return w
    }

    override fun draw(list: UiDrawList, text: String, x: Float, y: Float, size: Float, r: Float, g: Float, b: Float, a: Float): Float {
        val scale = size / data.lineHeight
        val pad = data.spread * scale
        var pen = x
        for (c in text) {
            val gl = glyph(c) ?: continue
            list.drawSdfGlyph(
                texture, pen - pad, y - pad, gl.w * scale, gl.h * scale,
                gl.x / data.width.toFloat(), gl.y / data.height.toFloat(),
                (gl.x + gl.w) / data.width.toFloat(), (gl.y + gl.h) / data.height.toFloat(),
                r, g, b, a,
            )
            pen += gl.advance * scale
        }
        return pen - x
    }
}
