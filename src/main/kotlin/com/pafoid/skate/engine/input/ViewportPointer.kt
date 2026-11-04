package com.pafoid.skate.engine.input

/**
 * On-screen rectangle that shows the game render target, in the units of the active [PointerSource]
 * (GLFW window coordinates in the game, ImGui desktop coordinates in the editor). Origin top-left, y down.
 */
data class ViewRect(val x: Float = 0f, val y: Float = 0f, val width: Float = 0f, val height: Float = 0f) {
    val isEmpty: Boolean get() = !(width > 0f && height > 0f)
}

/** Where the pointer is, in the same space as the [ViewRect] it is compared with. */
fun interface PointerSource {
    /** Writes the pointer position into [out] (x, y). */
    fun read(out: FloatArray)
}

/**
 * Pure conversions from a pointer position to the game view. Every consumer (picking, gizmo rays, runtime UI,
 * overlays) maps through here so they agree on the same pixel, wherever the window or viewport is on screen.
 */
object ViewportPointerMapping {

    /** Whether ([px], [py]) lies inside [rect] (left/top edges inclusive, right/bottom exclusive). */
    fun contains(px: Float, py: Float, rect: ViewRect): Boolean =
        !rect.isEmpty && px >= rect.x && px < rect.x + rect.width && py >= rect.y && py < rect.y + rect.height

    /** Offset from the rect's top-left corner, in pointer units, clamped to the rect. */
    fun local(px: Float, py: Float, rect: ViewRect): Pair<Float, Float> =
        (px - rect.x).coerceIn(0f, maxOf(rect.width, 0f)) to (py - rect.y).coerceIn(0f, maxOf(rect.height, 0f))

    /** Position as a fraction of the rect in [0, 1] (0, 0 = top-left); an empty rect maps to 0. */
    fun normalized(px: Float, py: Float, rect: ViewRect): Pair<Float, Float> {
        if (rect.isEmpty) return 0f to 0f
        return ((px - rect.x) / rect.width).coerceIn(0f, 1f) to ((py - rect.y) / rect.height).coerceIn(0f, 1f)
    }

    /**
     * Position in render-target pixels ([targetW] x [targetH], top-left origin), not clamped. The ratio absorbs
     * DPI scaling and a stretched blit; an empty rect leaves the point offset only.
     */
    fun toTarget(px: Float, py: Float, rect: ViewRect, targetW: Int, targetH: Int): Pair<Float, Float> {
        val (sx, sy) = scale(rect, targetW, targetH)
        return (px - rect.x) * sx to (py - rect.y) * sy
    }

    /** A pointer delta ([dx], [dy]) in pointer units converted to render-target pixels. */
    fun deltaToTarget(dx: Float, dy: Float, rect: ViewRect, targetW: Int, targetH: Int): Pair<Float, Float> {
        val (sx, sy) = scale(rect, targetW, targetH)
        return dx * sx to dy * sy
    }

    /** Normalized device coordinates in [-1, 1] (y up) for a ray through the pointer. */
    fun toNdc(px: Float, py: Float, rect: ViewRect): Pair<Float, Float> {
        val (u, v) = normalized(px, py, rect)
        return 2f * u - 1f to 1f - 2f * v
    }

    private fun scale(rect: ViewRect, targetW: Int, targetH: Int): Pair<Float, Float> =
        (if (rect.width > 0f) targetW / rect.width else 1f) to (if (rect.height > 0f) targetH / rect.height else 1f)
}
