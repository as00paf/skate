package com.pafoid.skate.engine.ui

import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.pow

/** Float rectangle in canvas units (origin top-left, y down). */
data class UiRectF(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
    val centerX: Float get() = x + w * 0.5f
    val centerY: Float get() = y + h * 0.5f

    fun contains(px: Float, py: Float): Boolean = px >= x && px < x + w && py >= y && py < y + h
}

enum class UiScaleMode { CONSTANT_PIXEL, SCALE_WITH_SCREEN, CONSTANT_PHYSICAL }

/** Canvas scale: how many screen pixels one canvas unit covers. */
object UiCanvasScaler {
    private val LN2 = ln(2f)

    /**
     * [match] blends width (0) and height (1) matching for [UiScaleMode.SCALE_WITH_SCREEN] on a log scale,
     * so a screen twice as wide and half as tall as the reference gets scale 1 at match 0.5.
     */
    fun scale(
        mode: UiScaleMode,
        referenceWidth: Float,
        referenceHeight: Float,
        screenWidth: Float,
        screenHeight: Float,
        match: Float = 0.5f,
        dpiScale: Float = 1f,
    ): Float = when (mode) {
        UiScaleMode.CONSTANT_PIXEL -> 1f
        UiScaleMode.CONSTANT_PHYSICAL -> dpiScale.coerceAtLeast(0.01f)
        UiScaleMode.SCALE_WITH_SCREEN -> {
            if (referenceWidth <= 0f || referenceHeight <= 0f || screenWidth <= 0f || screenHeight <= 0f) 1f
            else {
                val lw = ln(screenWidth / referenceWidth) / LN2
                val lh = ln(screenHeight / referenceHeight) / LN2
                2f.pow(lw + (lh - lw) * match.coerceIn(0f, 1f))
            }
        }
    }
}

enum class UiLayoutKind { NONE, VERTICAL, HORIZONTAL, GRID }

@Serializable
/** Arranges the children of a node. Alignment values are 0..1 per axis (0 = start, 0.5 = center, 1 = end). */
data class UiLayoutGroup(
    val kind: UiLayoutKind = UiLayoutKind.NONE,
    val paddingLeft: Float = 0f,
    val paddingTop: Float = 0f,
    val paddingRight: Float = 0f,
    val paddingBottom: Float = 0f,
    val spacing: Float = 0f,
    val alignX: Float = 0f,
    val alignY: Float = 0f,
    /** Stretch children across the cross axis (width for vertical, height for horizontal). */
    val fillCross: Boolean = false,
    val columns: Int = 1,
    val cellWidth: Float = 100f,
    val cellHeight: Float = 100f,
)

/**
 * GL- and ECS-free description of one element. Anchors are 0..1 fractions of the parent rect;
 * [x]/[y] is the pivot offset from the anchor and [width]/[height] is added to the anchor-rect size
 * (a stretched axis therefore uses negative values to inset). Inside a layout group, [width]/[height]
 * is the preferred size and anchors are ignored.
 */
class UiNodeSpec(
    val id: Int,
    val anchorMinX: Float = 0f, val anchorMinY: Float = 0f,
    val anchorMaxX: Float = 0f, val anchorMaxY: Float = 0f,
    val pivotX: Float = 0f, val pivotY: Float = 0f,
    val x: Float = 0f, val y: Float = 0f,
    val width: Float = 0f, val height: Float = 0f,
    val layout: UiLayoutGroup = UiLayoutGroup(),
    val children: List<UiNodeSpec> = emptyList(),
)

object UiLayout {
    /** Resolves every node of the tree (including [root]) to a rect in canvas units; [root] fills the canvas. */
    fun resolve(root: UiNodeSpec, canvasWidth: Float, canvasHeight: Float): Map<Int, UiRectF> {
        val out = HashMap<Int, UiRectF>()
        val rect = UiRectF(0f, 0f, canvasWidth, canvasHeight)
        out[root.id] = rect
        arrangeChildren(root, rect, out)
        return out
    }

    /** Rect of a node placed by anchors inside [parent]. */
    fun anchored(node: UiNodeSpec, parent: UiRectF): UiRectF {
        val ax0 = parent.x + node.anchorMinX * parent.w
        val ax1 = parent.x + node.anchorMaxX * parent.w
        val ay0 = parent.y + node.anchorMinY * parent.h
        val ay1 = parent.y + node.anchorMaxY * parent.h
        val w = (ax1 - ax0) + node.width
        val h = (ay1 - ay0) + node.height
        val left = ax0 + node.pivotX * (ax1 - ax0) + node.x - node.pivotX * w
        val top = ay0 + node.pivotY * (ay1 - ay0) + node.y - node.pivotY * h
        return UiRectF(left, top, w, h)
    }

    private fun arrangeChildren(node: UiNodeSpec, rect: UiRectF, out: MutableMap<Int, UiRectF>) {
        val g = node.layout
        val rects = when (g.kind) {
            UiLayoutKind.NONE -> node.children.map { anchored(it, rect) }
            UiLayoutKind.VERTICAL -> stack(node.children, rect, g, vertical = true)
            UiLayoutKind.HORIZONTAL -> stack(node.children, rect, g, vertical = false)
            UiLayoutKind.GRID -> grid(node.children, rect, g)
        }
        node.children.forEachIndexed { i, child ->
            out[child.id] = rects[i]
            arrangeChildren(child, rects[i], out)
        }
    }

    private fun stack(children: List<UiNodeSpec>, rect: UiRectF, g: UiLayoutGroup, vertical: Boolean): List<UiRectF> {
        val innerX = rect.x + g.paddingLeft
        val innerY = rect.y + g.paddingTop
        val innerW = (rect.w - g.paddingLeft - g.paddingRight).coerceAtLeast(0f)
        val innerH = (rect.h - g.paddingTop - g.paddingBottom).coerceAtLeast(0f)
        val mains = children.map { if (vertical) it.height else it.width }
        val total = mains.sum() + g.spacing * (children.size - 1).coerceAtLeast(0)
        val mainSpace = if (vertical) innerH else innerW
        var cursor = (mainSpace - total) * (if (vertical) g.alignY else g.alignX)
        return children.mapIndexed { i, c ->
            val main = mains[i]
            val crossSpace = if (vertical) innerW else innerH
            val cross = if (g.fillCross) crossSpace else if (vertical) c.width else c.height
            val crossPos = (crossSpace - cross) * (if (vertical) g.alignX else g.alignY)
            val r = if (vertical) UiRectF(innerX + crossPos, innerY + cursor, cross, main)
            else UiRectF(innerX + cursor, innerY + crossPos, main, cross)
            cursor += main + g.spacing
            r
        }
    }

    private fun grid(children: List<UiNodeSpec>, rect: UiRectF, g: UiLayoutGroup): List<UiRectF> {
        val cols = g.columns.coerceAtLeast(1)
        val rows = (children.size + cols - 1) / cols
        val gridW = cols * g.cellWidth + (cols - 1) * g.spacing
        val gridH = rows * g.cellHeight + (rows - 1).coerceAtLeast(0) * g.spacing
        val innerW = rect.w - g.paddingLeft - g.paddingRight
        val innerH = rect.h - g.paddingTop - g.paddingBottom
        val originX = rect.x + g.paddingLeft + (innerW - gridW) * g.alignX
        val originY = rect.y + g.paddingTop + (innerH - gridH) * g.alignY
        return children.indices.map { i ->
            UiRectF(
                originX + (i % cols) * (g.cellWidth + g.spacing),
                originY + (i / cols) * (g.cellHeight + g.spacing),
                g.cellWidth, g.cellHeight,
            )
        }
    }
}
