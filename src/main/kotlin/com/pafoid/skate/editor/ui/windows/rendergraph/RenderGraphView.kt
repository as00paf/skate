package com.pafoid.skate.editor.ui.windows.rendergraph

/**
 * Pan and zoom of the Render Graph canvas (EDT-54 §4.5), pure: screen = canvas origin + pan + world * zoom.
 * Coordinates passed in are relative to the canvas origin.
 */
class RenderGraphView {
    var zoom: Float = 1f
        private set
    var panX: Float = 0f
        private set
    var panY: Float = 0f
        private set

    fun toScreenX(worldX: Float) = panX + worldX * zoom
    fun toScreenY(worldY: Float) = panY + worldY * zoom
    fun toWorldX(screenX: Float) = (screenX - panX) / zoom
    fun toWorldY(screenY: Float) = (screenY - panY) / zoom

    fun panBy(dx: Float, dy: Float) {
        panX += dx
        panY += dy
    }

    /** Zooms by [factor] keeping the world point under ([anchorX], [anchorY]) fixed on screen. */
    fun zoomAt(factor: Float, anchorX: Float, anchorY: Float) {
        val wx = toWorldX(anchorX)
        val wy = toWorldY(anchorY)
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = anchorX - wx * zoom
        panY = anchorY - wy * zoom
    }

    /** 1:1 with the graph's top-left corner at the canvas origin. */
    fun actualSize() {
        zoom = 1f
        panX = 0f
        panY = 0f
    }

    /** Frames a [width] x [height] graph in a [viewW] x [viewH] canvas, centred, never zoomed in past 1:1. */
    fun fit(width: Float, height: Float, viewW: Float, viewH: Float) {
        if (width <= 0f || height <= 0f || viewW <= 0f || viewH <= 0f) return
        zoom = minOf(viewW / width, viewH / height, 1f).coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = (viewW - width * zoom) / 2f
        panY = (viewH - height * zoom) / 2f
    }

    companion object {
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 2f
    }
}

/** What is under the mouse on the graph, in priority order (a toggle beats its node). */
sealed class GraphHit {
    data class Toggle(val node: Int) : GraphHit()
    data class Expander(val node: Int) : GraphHit()
    data class Stage(val node: Int, val stage: Int) : GraphHit()
    data class Pin(val node: Int, val resource: String, val isInput: Boolean) : GraphHit()
    data class Node(val node: Int) : GraphHit()
}

object GraphHitTest {
    /** Hit test in world coordinates against the same rectangles the window draws. [pinRadius] in world units. */
    fun at(layout: GraphLayout, x: Float, y: Float, pinRadius: Float = 6f): GraphHit? {
        for (node in layout.nodes) {
            node.toggle?.let { if (it.contains(x, y)) return GraphHit.Toggle(node.index) }
            node.expander?.let { if (it.contains(x, y)) return GraphHit.Expander(node.index) }
            node.stageRows.forEachIndexed { s, r -> if (r.contains(x, y)) return GraphHit.Stage(node.index, s) }
            (node.inputs + node.outputs).forEach { pin ->
                val dx = pin.x - x
                val dy = pin.y - y
                if (dx * dx + dy * dy <= pinRadius * pinRadius) return GraphHit.Pin(node.index, pin.resource, pin.isInput)
            }
            if (node.rect.contains(x, y)) return GraphHit.Node(node.index)
        }
        return null
    }
}
