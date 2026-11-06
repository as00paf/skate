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

/** One draw: a run of quads sharing a texture and scissor. Pooled by [UiDrawList]: valid until its next [UiDrawList.clear]. */
class UiBatch internal constructor() {
    var texture: Texture? = null
        private set
    var scissor: UiRect? = null
        private set
    var firstQuad = 0
        private set
    var quadCount = 0

    internal fun reset(texture: Texture?, scissor: UiRect?, firstQuad: Int): UiBatch {
        this.texture = texture
        this.scissor = scissor
        this.firstQuad = firstQuad
        quadCount = 0
        return this
    }
}

/**
 * GL-free recorder of runtime-UI geometry: tinted textured quads, 9-slice panels, glyph quads and a scissor stack.
 * Vertices use the 2D batch layout (pos2, color4, uv2, texId, entityId); texId is 0 with a texture, -1 without;
 * entityId is [SDF_MODE] for SDF glyphs and 0 otherwise.
 * Consecutive quads with the same texture and scissor share a batch; quads fully outside the scissor are culled.
 *
 * Recording a frame like the previous one allocates nothing (VK-06): batches are pooled, the scissor rect of each
 * push is reused while its value is unchanged, and the vertex array only grows.
 */
class UiDrawList {
    private var vertices = FloatArray(256 * 4 * VERTEX_SIZE)
    private val scissors = ArrayList<UiRect>()
    private val batchList = ArrayList<UiBatch>()
    private val batchPool = ArrayList<UiBatch>()

    /** The rects pushed this frame, by push order: the next frame reuses a rect when its push matches (VK-06). */
    private val scissorCache = ArrayList<UiRect>()
    private var pushes = 0
    private val xs = FloatArray(4)
    private val ys = FloatArray(4)
    private val us = FloatArray(4)
    private val vs = FloatArray(4)

    var quadCount = 0
        private set

    val batches: List<UiBatch> get() = batchList
    val vertexData: FloatArray get() = vertices
    val currentScissor: UiRect? get() = scissors.lastOrNull()
    val scissorDepth: Int get() = scissors.size

    /** Pushes a clip rect, intersected with the current one. */
    fun pushScissor(x: Int, y: Int, w: Int, h: Int) {
        var x0 = x
        var y0 = y
        var x1 = x + w
        var y1 = y + h
        scissors.lastOrNull()?.let { p ->
            x0 = maxOf(x0, p.x); y0 = maxOf(y0, p.y); x1 = minOf(x1, p.x + p.w); y1 = minOf(y1, p.y + p.h)
        }
        val rw = maxOf(0, x1 - x0)
        val rh = maxOf(0, y1 - y0)
        val index = pushes++
        val cached = scissorCache.getOrNull(index)
        val rect = if (cached != null && cached.x == x0 && cached.y == y0 && cached.w == rw && cached.h == rh) cached
            else UiRect(x0, y0, rw, rh)
        if (index < scissorCache.size) scissorCache[index] = rect else scissorCache.add(rect)
        scissors.add(rect)
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
            ?: nextBatch().reset(texture, clip, quadCount).also { batchList.add(it) }
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
        nineSliceAxis(w, insets.left * scale, insets.right * scale, xs)
        nineSliceAxis(h, insets.top * scale, insets.bottom * scale, ys)
        us[0] = 0f; us[1] = insets.left / tw; us[2] = 1f - insets.right / tw; us[3] = 1f
        vs[0] = 0f; vs[1] = insets.top / th; vs[2] = 1f - insets.bottom / th; vs[3] = 1f
        for (row in 0..2) for (col in 0..2) {
            drawQuad(
                x + xs[col], y + ys[row], xs[col + 1] - xs[col], ys[row + 1] - ys[row],
                r, g, b, a, texture, us[col], vs[row], us[col + 1], vs[row + 1],
            )
        }
    }

    /** The pooled batch after those in use this frame. */
    private fun nextBatch(): UiBatch {
        val i = batchList.size
        if (i == batchPool.size) batchPool.add(UiBatch())
        return batchPool[i]
    }

    fun clear() {
        quadCount = 0
        batchList.clear()
        scissors.clear()
        pushes = 0
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
        fun nineSliceAxis(total: Float, start: Float, end: Float): FloatArray = nineSliceAxis(total, start, end, FloatArray(4))

        /** Writes the four slice positions along one axis into [out] (borders shrink uniformly if they do not fit). */
        fun nineSliceAxis(total: Float, start: Float, end: Float, out: FloatArray): FloatArray {
            val border = start + end
            val k = if (border > total && border > 0f) total / border else 1f
            out[0] = 0f; out[1] = start * k; out[2] = total - end * k; out[3] = total
            return out
        }
    }
}
