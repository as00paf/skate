package com.pafoid.skate.engine.render.ui

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.utils.RenderConsts2D.VERTEX_SIZE

/** Integer pixel rectangle in screen space (origin top-left, y down). */
data class UiRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    val isEmpty: Boolean get() = w <= 0 || h <= 0

    fun intersect(o: UiRect): UiRect {
        val x0 = maxOf(x, o.x)
        val y0 = maxOf(y, o.y)
        val x1 = minOf(x + w, o.x + o.w)
        val y1 = minOf(y + h, o.y + o.h)
        return UiRect(x0, y0, maxOf(0, x1 - x0), maxOf(0, y1 - y0))
    }
}

/** Source-texture border sizes (texels) for a 9-slice. */
data class NineSliceInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** One draw: a run of quads sharing a texture and scissor. */
class UiBatch(val texture: Texture?, val scissor: UiRect?, val firstQuad: Int) {
    var quadCount = 0
}

/**
 * GL-free recorder of runtime-UI geometry: tinted textured quads, 9-slice panels, glyph quads and a scissor stack.
 * Vertices use the 2D batch layout (pos2, color4, uv2, texId, entityId); texId is 0 with a texture, -1 without;
 * entityId is [SDF_MODE] for SDF glyphs and 0 otherwise.
 * Consecutive quads with the same texture and scissor share a batch; quads fully outside the scissor are culled.
 */
class UiDrawList {
    private var vertices = FloatArray(256 * 4 * VERTEX_SIZE)
    private val scissors = ArrayList<UiRect>()
    private val batchList = ArrayList<UiBatch>()

    var quadCount = 0
        private set

    val batches: List<UiBatch> get() = batchList
    val vertexData: FloatArray get() = vertices
    val currentScissor: UiRect? get() = scissors.lastOrNull()
    val scissorDepth: Int get() = scissors.size

    /** Pushes a clip rect, intersected with the current one. */
    fun pushScissor(x: Int, y: Int, w: Int, h: Int) {
        val r = UiRect(x, y, w, h)
        scissors.add(scissors.lastOrNull()?.intersect(r) ?: r)
    }

    fun popScissor() {
        check(scissors.isNotEmpty()) { "popScissor without matching pushScissor" }
        scissors.removeAt(scissors.size - 1)
    }

    fun drawQuad(
        x: Float, y: Float, w: Float, h: Float,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f,
        texture: Texture? = null,
        u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f,
        sdf: Boolean = false,
    ) {
        if (w <= 0f || h <= 0f) return
        val clip = currentScissor
        if (clip != null) {
            if (clip.isEmpty) return
            if (x >= clip.x + clip.w || x + w <= clip.x || y >= clip.y + clip.h || y + h <= clip.y) return
        }
        val batch = batchList.lastOrNull()?.takeIf { it.texture === texture && it.scissor == clip }
            ?: UiBatch(texture, clip, quadCount).also { batchList.add(it) }
        ensureCapacity(quadCount + 1)
        var o = quadCount * 4 * VERTEX_SIZE
        val texId = if (texture != null) 0f else -1f
        val mode = if (sdf) SDF_MODE else 0f
        // Corner order matches RenderBatch2D: (0,0) (1,0) (1,1) (0,1)
        o = vertex(o, x, y, r, g, b, a, u0, v0, texId, mode)
        o = vertex(o, x + w, y, r, g, b, a, u1, v0, texId, mode)
        o = vertex(o, x + w, y + h, r, g, b, a, u1, v1, texId, mode)
        vertex(o, x, y + h, r, g, b, a, u0, v1, texId, mode)
        quadCount++
        batch.quadCount++
    }

    /** One glyph quad from an atlas region. */
    fun drawGlyph(
        texture: Texture, x: Float, y: Float, w: Float, h: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f,
    ) = drawQuad(x, y, w, h, r, g, b, a, texture, u0, v0, u1, v1)

    /** One glyph quad whose atlas alpha is a signed distance field (alpha-tested edge in the shader). */
    fun drawSdfGlyph(
        texture: Texture, x: Float, y: Float, w: Float, h: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f,
    ) = drawQuad(x, y, w, h, r, g, b, a, texture, u0, v0, u1, v1, sdf = true)

    /**
     * 9-slice panel. Corners keep their texel size times [scale] (shrunk uniformly if the panel is smaller than the
     * borders); edges and center stretch.
     */
    fun drawNineSlice(
        x: Float, y: Float, w: Float, h: Float,
        texture: Texture, insets: NineSliceInsets, scale: Float = 1f,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f,
    ) {
        val tw = texture.width.toFloat()
        val th = texture.height.toFloat()
        if (w <= 0f || h <= 0f || tw <= 0f || th <= 0f) return
        val xs = nineSliceAxis(w, insets.left * scale, insets.right * scale)
        val ys = nineSliceAxis(h, insets.top * scale, insets.bottom * scale)
        val us = floatArrayOf(0f, insets.left / tw, 1f - insets.right / tw, 1f)
        val vs = floatArrayOf(0f, insets.top / th, 1f - insets.bottom / th, 1f)
        for (row in 0..2) for (col in 0..2) {
            drawQuad(
                x + xs[col], y + ys[row], xs[col + 1] - xs[col], ys[row + 1] - ys[row],
                r, g, b, a, texture, us[col], vs[row], us[col + 1], vs[row + 1],
            )
        }
    }

    fun clear() {
        quadCount = 0
        batchList.clear()
        scissors.clear()
    }

    private fun ensureCapacity(quads: Int) {
        val needed = quads * 4 * VERTEX_SIZE
        if (needed > vertices.size) vertices = vertices.copyOf(maxOf(needed, vertices.size * 2))
    }

    private fun vertex(o: Int, x: Float, y: Float, r: Float, g: Float, b: Float, a: Float, u: Float, v: Float, texId: Float, mode: Float): Int {
        vertices[o] = x; vertices[o + 1] = y
        vertices[o + 2] = r; vertices[o + 3] = g; vertices[o + 4] = b; vertices[o + 5] = a
        vertices[o + 6] = u; vertices[o + 7] = v
        vertices[o + 8] = texId
        vertices[o + 9] = mode
        return o + VERTEX_SIZE
    }

    companion object {
        /**
         * Written to the entityId vertex slot to make the shader treat the texture alpha as a distance field.
         * Negative on purpose: encoded entity ids are always >= 1, so world sprites can never match it.
         */
        const val SDF_MODE = -2f

        /** Offsets of the 4 cut points along one axis; borders shrink proportionally when they do not fit. */
        fun nineSliceAxis(total: Float, start: Float, end: Float): FloatArray {
            val border = start + end
            val k = if (border > total && border > 0f) total / border else 1f
            return floatArrayOf(0f, start * k, total - end * k, total)
        }
    }
}
