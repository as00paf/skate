package com.pafoid.skate.editor.ui.windows.rendergraph

import com.pafoid.skate.engine.render.graph.PassInfo
import com.pafoid.skate.engine.render.graph.RenderGraphInfo
import com.pafoid.skate.engine.render.graph.RenderResourceIds

/** Axis-aligned rectangle in graph (world) units. */
data class GraphRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
    fun contains(px: Float, py: Float): Boolean = px >= x && px <= right && py >= y && py <= bottom
    fun overlaps(o: GraphRect): Boolean = x < o.right && o.x < right && y < o.bottom && o.y < bottom
}

/** Flow direction: passes left to right (wide bottom dock) or top to bottom (narrow side dock). */
enum class GraphOrientation { HORIZONTAL, VERTICAL }

/** Sizes in graph units at zoom 1 (the window passes values scaled from its font). */
data class GraphLayoutStyle(
    val lineHeight: Float = 18f,
    val padding: Float = 8f,
    val columnGap: Float = 56f,
    val laneGap: Float = 12f,
    val margin: Float = 16f,
    val toggleSize: Float = 16f,
    val minNodeWidth: Float = 150f,
)

/**
 * A resource pin of a node. [x]/[y] is where edges attach. An input with no earlier writer has [hasWriter] false and
 * is drawn dimmed with no edge ("no writer", e.g. Geometry's shadow maps when the device has no shadow maps).
 */
data class GraphPin(val resource: String, val isInput: Boolean, val x: Float, val y: Float, val labelX: Float, val labelY: Float, val hasWriter: Boolean)

/**
 * One pass box. Rows, top to bottom: header (expand arrow, name, toggle), timing, share bar, one row per pin pair,
 * then one row per stage when expanded.
 */
data class GraphNode(
    val index: Int,
    val pass: PassInfo,
    val rect: GraphRect,
    val header: GraphRect,
    val toggle: GraphRect?,
    val expander: GraphRect?,
    val timingRow: GraphRect,
    val barRow: GraphRect,
    val inputs: List<GraphPin>,
    val outputs: List<GraphPin>,
    val stageRows: List<GraphRect>,
)

/**
 * A data edge from the writer's output pin to a reader's input pin. [lane] 0 is a direct link between neighbouring
 * passes; lanes 1.. run around the nodes (above them horizontally, left of them vertically). [points] is the polyline.
 */
data class GraphEdge(
    val fromNode: Int,
    val toNode: Int,
    val resource: String,
    val lane: Int,
    val points: List<Pair<Float, Float>>,
)

data class GraphLayout(
    val version: Int,
    val orientation: GraphOrientation,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val width: Float,
    val height: Float,
    val laneCount: Int,
)

/**
 * Pure layout of the Render Graph window (EDT-54 §4): no ImGui. Text widths come from [measure] (ImGui.calcTextSize
 * in the window, a fixed-width stub in tests) and labels from [label] (translation).
 */
object RenderGraphLayout {

    /** Widest timing text a node shows, used to size every node the same way whatever the numbers are. */
    const val TIMING_TEMPLATE = "GPU 00.00 ms  CPU 00.00 ms"

    fun compute(
        info: RenderGraphInfo,
        measure: (String) -> Float,
        label: (String) -> String,
        expanded: Set<String> = emptySet(),
        orientation: GraphOrientation = GraphOrientation.HORIZONTAL,
        style: GraphLayoutStyle = GraphLayoutStyle(),
    ): GraphLayout {
        val passes = info.passes
        val sizes = passes.map { nodeSize(it, it.id in expanded, measure, label, style) }
        val links = links(passes)
        val lanes = assignLanes(links)
        val laneCount = lanes.maxOrNull() ?: 0
        val horizontal = orientation == GraphOrientation.HORIZONTAL

        // Nodes sit after the lane band on the cross axis, one per column on the main axis.
        val crossStart = style.margin + laneCount * style.laneGap + if (laneCount > 0) style.laneGap else 0f
        var main = style.margin
        val nodes = passes.mapIndexed { i, pass ->
            val (w, h) = sizes[i]
            val rect = if (horizontal) GraphRect(main, crossStart, w, h) else GraphRect(crossStart, main, w, h)
            main += (if (horizontal) w else h) + style.columnGap
            node(i, pass, rect, pass.id in expanded, horizontal, passes, style)
        }

        val stub = style.columnGap * 0.3f
        val edges = links.mapIndexed { k, link ->
            val from = nodes[link.from].outputs.first { it.resource == link.resource }
            val to = nodes[link.to].inputs[link.inputIndex]
            val lane = lanes[k]
            val points = if (lane == 0) {
                listOf(from.x to from.y, to.x to to.y)
            } else {
                val laneAt = crossStart - lane * style.laneGap
                if (horizontal) listOf(
                    from.x to from.y, from.x + stub to from.y, from.x + stub to laneAt,
                    to.x - stub to laneAt, to.x - stub to to.y, to.x to to.y,
                ) else listOf(
                    from.x to from.y, from.x to from.y + stub, laneAt to from.y + stub,
                    laneAt to to.y - stub, to.x to to.y - stub, to.x to to.y,
                )
            }
            GraphEdge(link.from, link.to, link.resource, lane, points)
        }

        val mainEnd = main - style.columnGap + style.margin
        val crossEnd = crossStart + (nodes.maxOfOrNull { if (horizontal) it.rect.h else it.rect.w } ?: 0f) + style.margin
        return GraphLayout(
            info.version, orientation, nodes, edges,
            width = if (horizontal) mainEnd else crossEnd,
            height = if (horizontal) crossEnd else mainEnd,
            laneCount = laneCount,
        )
    }

    /**
     * Horizontal unless the window is clearly taller than wide and too narrow for the horizontal graph
     * (EDT-54 §4.4: available width under 60 % of the laid-out width).
     */
    fun chooseOrientation(availableWidth: Float, availableHeight: Float, horizontalWidth: Float): GraphOrientation =
        if (availableHeight > availableWidth && availableWidth < 0.6f * horizontalWidth) GraphOrientation.VERTICAL
        else GraphOrientation.HORIZONTAL

    /** A read of [resource] by pass [to] (its [inputIndex]-th input), written last by pass [from]. */
    internal data class Link(val from: Int, val to: Int, val resource: String, val inputIndex: Int)

    /** Each read links to the latest earlier pass that writes the resource; reads with no writer get no link. */
    internal fun links(passes: List<PassInfo>): List<Link> = passes.flatMapIndexed { to, pass ->
        pass.reads.mapIndexedNotNull { k, resource ->
            val from = (to - 1 downTo 0).firstOrNull { resource in passes[it].writes } ?: return@mapIndexedNotNull null
            Link(from, to, resource, k)
        }
    }

    /**
     * Lane per link: 0 for neighbouring columns, else the lowest lane whose links do not overlap this one's column
     * span (greedy interval colouring). Shortest spans are placed first, so they take the inner lanes and long links
     * run outside them, which keeps crossings down.
     */
    internal fun assignLanes(links: List<Link>): List<Int> {
        val lanes = IntArray(links.size)
        val laneSpans = ArrayList<MutableList<IntRange>>()
        links.indices.filter { links[it].to - links[it].from > 1 }
            .sortedWith(compareBy({ links[it].to - links[it].from }, { links[it].from }))
            .forEach { k ->
                val span = links[k].from..links[k].to
                val free = laneSpans.indexOfFirst { used -> used.none { overlaps(it, span) } }
                val lane = if (free >= 0) free else laneSpans.size.also { laneSpans += mutableListOf<IntRange>() }
                laneSpans[lane] += span
                lanes[k] = lane + 1
            }
        return lanes.toList()
    }

    /** Spans overlap when they share more than an end column (edges meeting at one column use different sides). */
    internal fun overlaps(a: IntRange, b: IntRange): Boolean = a.first < b.last && b.first < a.last

    private fun pinRows(pass: PassInfo) = maxOf(pass.reads.size, pass.writes.size)

    private fun stageRows(pass: PassInfo, expanded: Boolean) = if (expanded) pass.stages.size else 0

    private fun nodeSize(pass: PassInfo, expanded: Boolean, measure: (String) -> Float, label: (String) -> String, style: GraphLayoutStyle): Pair<Float, Float> {
        val headerW = style.toggleSize * 2 + measure(label(pass.labelKey)) + style.padding * 2
        val timingW = measure(TIMING_TEMPLATE)
        val pinW = (0 until pinRows(pass)).maxOfOrNull { r ->
            val inW = pass.reads.getOrNull(r)?.let { measure(label(resourceKey(it))) } ?: 0f
            val outW = pass.writes.getOrNull(r)?.let { measure(label(resourceKey(it))) } ?: 0f
            inW + outW + style.padding * 3
        } ?: 0f
        val stageW = if (expanded) pass.stages.maxOfOrNull { style.toggleSize + measure(label(it.labelKey)) + style.padding } ?: 0f else 0f
        val width = maxOf(style.minNodeWidth, headerW, timingW + style.padding * 2, pinW, stageW + style.padding * 2)
        val rows = 3 + pinRows(pass) + stageRows(pass, expanded)
        return width to rows * style.lineHeight + style.padding * 2
    }

    private fun node(index: Int, pass: PassInfo, rect: GraphRect, expanded: Boolean, horizontal: Boolean, passes: List<PassInfo>, style: GraphLayoutStyle): GraphNode {
        val lh = style.lineHeight
        fun row(r: Int) = GraphRect(rect.x + style.padding, rect.y + style.padding + r * lh, rect.w - style.padding * 2, lh)
        val header = row(0)
        val ts = style.toggleSize
        val toggle = if (pass.required) null else GraphRect(header.right - ts, header.y + (lh - ts) / 2, ts, ts)
        val expander = if (pass.stages.isEmpty()) null else GraphRect(header.x, header.y + (lh - ts) / 2, ts, ts)
        val writtenBefore = { resource: String -> (0 until index).any { resource in passes[it].writes } }

        val inputs = pass.reads.mapIndexed { k, resource ->
            val r = row(3 + k)
            val (px, py) = if (horizontal) rect.x to r.y + lh / 2
            else rect.x + rect.w * (k + 1) / (pass.reads.size + 1) to rect.y
            GraphPin(resource, true, px, py, r.x, r.y, writtenBefore(resource))
        }
        val outputs = pass.writes.mapIndexed { k, resource ->
            val r = row(3 + k)
            val (px, py) = if (horizontal) rect.right to r.y + lh / 2
            else rect.x + rect.w * (k + 1) / (pass.writes.size + 1) to rect.bottom
            GraphPin(resource, false, px, py, r.right, r.y, true)
        }
        val firstStage = 3 + pinRows(pass)
        val stages = (0 until stageRows(pass, expanded)).map { row(firstStage + it) }
        return GraphNode(index, pass, rect, header, toggle, expander, row(1), row(2), inputs, outputs, stages)
    }

    /** Translation key of a resource name (mirrors `RenderResourceIds.labelKey`). */
    fun resourceKey(resource: String): String = RenderResourceIds.labelKey(resource)
}

/**
 * Caches the Render Graph layouts (EDT-54 §4.6): they are recomputed only when the graph version, the expanded passes
 * or the font size change. Each frame only re-chooses the orientation for the canvas size, from the cached
 * horizontal width; the vertical layout is computed the first time it is needed.
 */
class RenderGraphLayoutCache(private val compute: (GraphOrientation) -> GraphLayout) {
    private var key: List<Any>? = null
    private var horizontal: GraphLayout? = null
    private var vertical: GraphLayout? = null

    /** Number of layouts computed so far (tests). */
    var computeCount = 0
        private set

    fun get(version: Int, expanded: Set<String>, fontSize: Float, viewW: Float, viewH: Float): GraphLayout {
        val k = listOf(version, expanded.toSet(), fontSize)
        if (k != key) {
            key = k
            horizontal = null
            vertical = null
        }
        val h = horizontal ?: run(GraphOrientation.HORIZONTAL).also { horizontal = it }
        return when (RenderGraphLayout.chooseOrientation(viewW, viewH, h.width)) {
            GraphOrientation.HORIZONTAL -> h
            GraphOrientation.VERTICAL -> vertical ?: run(GraphOrientation.VERTICAL).also { vertical = it }
        }
    }

    private fun run(orientation: GraphOrientation): GraphLayout {
        computeCount++
        return compute(orientation)
    }
}
