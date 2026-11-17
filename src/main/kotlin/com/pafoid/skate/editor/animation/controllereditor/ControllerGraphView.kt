package com.pafoid.skate.editor.animation.controllereditor

import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset.Companion.ANY_STATE_NODE
import com.pafoid.skate.engine.animation.graph.NodePos
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Pure geometry of the Animator window's graph canvas (ANI-21 §5.1): box positions (stored or laid out
 * automatically), arrows, hit tests and the pan/zoom view. Canvas units; the window converts with [toScreen] and
 * [toCanvas]. No ImGui, unit-tested.
 */
class ControllerGraphView {
    /** Canvas point shown at the top-left of the canvas widget. */
    var panX = -40f
    var panY = -40f
    var zoom = 1f
        set(value) { field = value.coerceIn(MIN_ZOOM, MAX_ZOOM) }

    fun toScreen(x: Float, y: Float, originX: Float, originY: Float) = (originX + (x - panX) * zoom) to (originY + (y - panY) * zoom)
    fun toCanvas(sx: Float, sy: Float, originX: Float, originY: Float) = (panX + (sx - originX) / zoom) to (panY + (sy - originY) / zoom)

    /** Zooms by [factor] keeping the canvas point under the screen point ([sx], [sy]) still. */
    fun zoomAt(factor: Float, sx: Float, sy: Float, originX: Float, originY: Float) {
        val (cx, cy) = toCanvas(sx, sy, originX, originY)
        zoom *= factor
        panX = cx - (sx - originX) / zoom
        panY = cy - (sy - originY) / zoom
    }

    fun panBy(dxScreen: Float, dyScreen: Float) { panX -= dxScreen / zoom; panY -= dyScreen / zoom }

    /** Fits every box in a [width] x [height] canvas widget, at most at 100 %. */
    fun frameAll(positions: Map<String, NodePos>, width: Float, height: Float) {
        if (positions.isEmpty() || width <= 0f || height <= 0f) return
        val minX = positions.values.minOf { it.x } - MARGIN
        val minY = positions.values.minOf { it.y } - MARGIN
        val maxX = positions.values.maxOf { it.x } + NODE_W + MARGIN
        val maxY = positions.values.maxOf { it.y } + NODE_H + MARGIN
        zoom = min(1f, min(width / (maxX - minX), height / (maxY - minY)))
        panX = (minX + maxX) / 2f - width / 2f / zoom
        panY = (minY + maxY) / 2f - height / 2f / zoom
    }

    /** A drawn arrow: every transition between [from] and [to] (in list order) shares it. */
    data class Arrow(val from: String, val to: String, val indices: List<Int>, val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    companion object {
        const val NODE_W = 150f
        const val NODE_H = 40f
        const val COLUMN = 220f
        const val ROW = 90f
        const val MARGIN = 40f
        const val PAIR_OFFSET = 8f
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 2.5f

        /** The node key of a transition source: the state, or [ANY_STATE_NODE]. */
        fun sourceNode(from: String?) = from ?: ANY_STATE_NODE

        /**
         * Box top-left corners: the stored [AnimationControllerAsset.layout], and for states it lacks an automatic
         * grid: the default state first, then breadth-first along the transitions, one column per step, below any
         * stored box. The Any State box appears when an Any State transition exists.
         */
        fun positions(asset: AnimationControllerAsset): Map<String, NodePos> {
            val out = LinkedHashMap<String, NodePos>()
            val names = asset.states.map { it.name }
            for (n in names) asset.layout[n]?.let { out[n] = it }
            val missing = names.filter { it !in out }
            if (missing.isNotEmpty()) {
                val depth = LinkedHashMap<String, Int>()
                val queue = ArrayDeque<String>()
                fun visit(n: String, d: Int) { if (n !in depth) { depth[n] = d; queue.add(n) } }
                visit(asset.defaultState, 0)
                while (queue.isNotEmpty()) {
                    val n = queue.removeFirst()
                    asset.transitions.filter { it.from == n || it.from == null }.forEach { visit(it.to, depth.getValue(n) + 1) }
                }
                names.forEach { visit(it, 0) }
                val top = if (out.isEmpty()) 0f else out.values.maxOf { it.y } + ROW
                val rows = HashMap<Int, Int>()
                for (n in missing) {
                    val d = depth.getValue(n)
                    val r = rows.merge(d, 1, Int::plus)!! - 1
                    out[n] = NodePos(d * COLUMN, top + r * ROW)
                }
            }
            if (asset.transitions.any { it.from == null }) {
                out[ANY_STATE_NODE] = asset.layout[ANY_STATE_NODE] ?: NodePos(
                    (out.values.minOfOrNull { it.x } ?: 0f),
                    (out.values.minOfOrNull { it.y } ?: 0f) - ROW,
                )
            }
            return out
        }

        /** The box under canvas point ([x], [y]), topmost (last drawn) first. */
        fun nodeAt(positions: Map<String, NodePos>, x: Float, y: Float): String? =
            positions.entries.lastOrNull { (_, p) -> x >= p.x && x <= p.x + NODE_W && y >= p.y && y <= p.y + NODE_H }?.key

        /** Boxes intersecting the rectangle between two canvas corners. */
        fun nodesIn(positions: Map<String, NodePos>, ax: Float, ay: Float, bx: Float, by: Float): Set<String> {
            val x0 = min(ax, bx); val x1 = max(ax, bx); val y0 = min(ay, by); val y1 = max(ay, by)
            return positions.filter { (_, p) -> p.x <= x1 && p.x + NODE_W >= x0 && p.y <= y1 && p.y + NODE_H >= y0 }.keys
        }

        /**
         * One arrow per (source, target) pair, from box edge to box edge. A→B and B→A are offset to either side so
         * both stay visible; a transition from a state to itself is not drawn as an arrow.
         */
        fun arrows(asset: AnimationControllerAsset, positions: Map<String, NodePos>): List<Arrow> {
            val groups = LinkedHashMap<Pair<String, String>, MutableList<Int>>()
            asset.transitions.forEachIndexed { i, t ->
                val from = sourceNode(t.from)
                if (from != t.to) groups.getOrPut(from to t.to) { mutableListOf() }.add(i)
            }
            return groups.mapNotNull { (key, indices) ->
                val a = positions[key.first] ?: return@mapNotNull null
                val b = positions[key.second] ?: return@mapNotNull null
                val ax = a.x + NODE_W / 2; val ay = a.y + NODE_H / 2
                val bx = b.x + NODE_W / 2; val by = b.y + NODE_H / 2
                val len = hypot(bx - ax, by - ay).takeIf { it > 1e-3f } ?: return@mapNotNull null
                val nx = -(by - ay) / len; val ny = (bx - ax) / len
                val offset = if ((key.second to key.first) in groups) PAIR_OFFSET else 0f
                val (sx, sy) = boxExit(ax, ay, bx - ax, by - ay)
                val (ex, ey) = boxExit(bx, by, ax - bx, ay - by)
                Arrow(key.first, key.second, indices, sx + nx * offset, sy + ny * offset, ex + nx * offset, ey + ny * offset)
            }
        }

        /** Where the ray from a box centre along (dx, dy) leaves the box. */
        private fun boxExit(cx: Float, cy: Float, dx: Float, dy: Float): Pair<Float, Float> {
            val tx = if (abs(dx) < 1e-6f) Float.MAX_VALUE else (NODE_W / 2) / abs(dx)
            val ty = if (abs(dy) < 1e-6f) Float.MAX_VALUE else (NODE_H / 2) / abs(dy)
            val t = min(tx, ty)
            return (cx + dx * t) to (cy + dy * t)
        }

        /** The arrow within [tolerance] canvas units of ([x], [y]), nearest first. */
        fun arrowAt(arrows: List<Arrow>, x: Float, y: Float, tolerance: Float = 6f): Arrow? =
            arrows.map { it to distanceToSegment(x, y, it.x0, it.y0, it.x1, it.y1) }
                .filter { it.second <= tolerance }
                .minByOrNull { it.second }?.first

        fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 <= 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
            return hypot(px - (ax + t * dx), py - (ay + t * dy))
        }
    }
}
