package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.render.ui.GridFont
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_CLAMP
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameteri
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Default UI font: a monospaced glyph atlas rasterized once with Java2D. Stand-in until SDF fonts (GUI-04).
 * Must be called on the GL thread.
 */
object UiBuiltInFont {
    const val COLUMNS = 16
    const val ROWS = 6
    const val FIRST_CHAR = 32
    private const val CELL_W = 24
    private const val CELL_H = 48

    fun create(): GridFont {
        System.setProperty("java.awt.headless", "true")
        val image = BufferedImage(COLUMNS * CELL_W, ROWS * CELL_H, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.font = Font(Font.MONOSPACED, Font.BOLD, 40)
        g.color = Color.WHITE
        val m = g.fontMetrics
        val baseline = (CELL_H - (m.ascent + m.descent)) / 2 + m.ascent
        for (i in 0 until COLUMNS * ROWS) {
            val c = (FIRST_CHAR + i).toChar()
            val cx = (i % COLUMNS) * CELL_W + (CELL_W - m.charWidth(c)) / 2
            g.drawString(c.toString(), cx, (i / COLUMNS) * CELL_H + baseline)
        }
        g.dispose()
        return GridFont(upload(image), COLUMNS, ROWS, FIRST_CHAR, CELL_W.toFloat() / CELL_H)
    }

    private fun upload(image: BufferedImage): Texture {
        val w = image.width
        val h = image.height
        val buffer = BufferUtils.createByteBuffer(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val p = image.getRGB(x, y)
            buffer.put((p shr 16 and 0xFF).toByte()).put((p shr 8 and 0xFF).toByte())
                .put((p and 0xFF).toByte()).put((p ushr 24).toByte())
        }
        buffer.flip()
        val texture = Texture(w, h, 1, 4, false, null)
        texture.texId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, texture.texId)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
        return texture
    }
}
