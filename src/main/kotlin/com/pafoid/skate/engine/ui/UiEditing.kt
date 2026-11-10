package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.getComponent
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which part of an element rect a viewport drag grabs. */
enum class UiHandle(val west: Boolean = false, val east: Boolean = false, val north: Boolean = false, val south: Boolean = false) {
    MOVE,
    /** The pivot marker: moves the pivot inside the rect, the rect stays put. */
    PIVOT,
    /** The anchor markers (min corner / max corner of the anchor rect, in the parent): the rect stays put. */
    ANCHOR_MIN, ANCHOR_MAX,
    N(north = true), S(south = true), W(west = true), E(east = true),
    NW(west = true, north = true), NE(east = true, north = true), SW(west = true, south = true), SE(east = true, south = true),
}

/**
 * State captured when a viewport drag starts: the element's rect and size, the parent rect its anchors refer to,
 * and the pointer position, all in canvas units. Layout-managed elements only change size.
 */
data class UiDragStart(
    val rect: UiRectF,
    val parent: UiRectF,
    val width: Float,
    val height: Float,
    val pointerX: Float,
    val pointerY: Float,
    val layoutManaged: Boolean,
)

/** Pure math behind viewport editing of UI elements. All deltas and rects are in canvas units. */
object UiEditing {
    private const val MIN_SIZE = 1f

    fun move(e: UIElementComponent, dx: Float, dy: Float) {
        e.x += dx
        e.y += dy
    }

    /**
     * Drags [handle] by ([dx], [dy]) keeping the opposite edges fixed. Works for point and stretched anchors:
     * the rect is `anchorRect + x - pivot * size`, so moving one edge by d shifts x by d * (1 - pivot) (west/north)
     * or d * pivot (east/south).
     */
    fun resize(e: UIElementComponent, handle: UiHandle, dx: Float, dy: Float) {
        if (handle == UiHandle.MOVE) return move(e, dx, dy)
        val w = e.rect.w
        val h = e.rect.h
        if (handle.west || handle.east) {
            val d = if (handle.west) -dx else dx
            if (w + d >= MIN_SIZE) {
                e.width += d
                e.x += if (handle.west) -d * (1f - e.pivotX) else d * e.pivotX
            }
        }
        if (handle.north || handle.south) {
            val d = if (handle.north) -dy else dy
            if (h + d >= MIN_SIZE) {
                e.height += d
                e.y += if (handle.north) -d * (1f - e.pivotY) else d * e.pivotY
            }
        }
    }

    /**
     * Rect of [start] after dragging [handle] by the total ([dx], [dy]) since the drag began. Moved edges snap to
     * multiples of [snap] canvas units (0 = off); a resize never goes below the minimum size. Pure: the element is
     * untouched, apply the result with [setRect].
     */
    fun dragRect(start: UiRectF, handle: UiHandle, dx: Float, dy: Float, snap: Float = 0f): UiRectF {
        if (handle == UiHandle.MOVE) {
            return UiRectF(snapped(start.x + dx, snap), snapped(start.y + dy, snap), start.w, start.h)
        }
        var left = start.x
        var right = start.right
        var top = start.y
        var bottom = start.bottom
        if (handle.west) left = snapped(left + dx, snap).coerceAtMost(right - MIN_SIZE)
        if (handle.east) right = snapped(right + dx, snap).coerceAtLeast(left + MIN_SIZE)
        if (handle.north) top = snapped(top + dy, snap).coerceAtMost(bottom - MIN_SIZE)
        if (handle.south) bottom = snapped(bottom + dy, snap).coerceAtLeast(top + MIN_SIZE)
        return UiRectF(left, top, right - left, bottom - top)
    }

    /** Snap step for pivot and anchor fractions while snapping is on. */
    const val FRACTION_SNAP = 0.05f

    /** Captures [go]'s drag start; null outside a canvas. */
    fun beginDrag(go: GameObject, e: UIElementComponent, pointerX: Float, pointerY: Float): UiDragStart? {
        val parent = parentRect(go) ?: return null
        return UiDragStart(e.rect, parent, e.width, e.height, pointerX, pointerY, isLayoutManaged(go))
    }

    /**
     * Applies a drag of [handle] with the pointer now at ([pointerX], [pointerY]); always computed from [start], so
     * snapping never loses small motions. [snap] is the rect grid step in canvas units (0 = off); pivot and anchors
     * then snap to [FRACTION_SNAP].
     */
    fun drag(e: UIElementComponent, start: UiDragStart, handle: UiHandle, pointerX: Float, pointerY: Float, snap: Float = 0f) {
        val fractionSnap = if (snap > 0f) FRACTION_SNAP else 0f
        val p = start.parent
        val r = start.rect
        when (handle) {
            UiHandle.PIVOT -> if (!start.layoutManaged && r.w > 0f && r.h > 0f) {
                setPivotKeepingRect(e, p, r, snapped((pointerX - r.x) / r.w, fractionSnap), snapped((pointerY - r.y) / r.h, fractionSnap))
            }
            UiHandle.ANCHOR_MIN, UiHandle.ANCHOR_MAX -> if (!start.layoutManaged && p.w > 0f && p.h > 0f) {
                setAnchorKeepingRect(
                    e, p, r, handle,
                    snapped((pointerX - p.x) / p.w, fractionSnap), snapped((pointerY - p.y) / p.h, fractionSnap),
                )
            }
            else -> {
                val target = dragRect(r, handle, pointerX - start.pointerX, pointerY - start.pointerY, snap)
                if (start.layoutManaged) {
                    if (handle == UiHandle.MOVE) return
                    e.width = start.width + (target.w - r.w)
                    e.height = start.height + (target.h - r.h)
                } else {
                    setRect(e, p, target)
                }
            }
        }
    }

    /**
     * What a click at ([px], [py]) grabs on the selected element. Edge and corner handles win, then the pivot, then
     * the anchor markers (skipped when [editAnchors] is false, e.g. inside a layout group), then the interior (move).
     */
    fun hitGizmo(e: UIElementComponent, parent: UiRectF?, px: Float, py: Float, tolerance: Float, editAnchors: Boolean = true): UiHandle? {
        val rectHit = hitHandle(e.rect, px, py, tolerance)
        if (rectHit != null && rectHit != UiHandle.MOVE) return rectHit
        fun near(p: Pair<Float, Float>) = abs(px - p.first) <= tolerance && abs(py - p.second) <= tolerance
        if (editAnchors && near(pivotPoint(e, e.rect))) return UiHandle.PIVOT
        if (editAnchors && parent != null) {
            if (near(anchorPoint(e, parent, UiHandle.ANCHOR_MAX))) return UiHandle.ANCHOR_MAX
            if (near(anchorPoint(e, parent, UiHandle.ANCHOR_MIN))) return UiHandle.ANCHOR_MIN
        }
        return rectHit
    }

    /** [value] rounded to the nearest multiple of [step]; unchanged when [step] is 0 or less. */
    fun snapped(value: Float, step: Float): Float = if (step > 0f) (value / step).roundToInt() * step else value

    /**
     * Writes x/y/width/height so the element resolves to [target] inside [parent] with its current anchors and
     * pivot: the inverse of [UiLayout.anchored].
     */
    fun setRect(e: UIElementComponent, parent: UiRectF, target: UiRectF) {
        val ax0 = parent.x + e.anchorMinX * parent.w
        val aw = (e.anchorMaxX - e.anchorMinX) * parent.w
        val ay0 = parent.y + e.anchorMinY * parent.h
        val ah = (e.anchorMaxY - e.anchorMinY) * parent.h
        e.width = target.w - aw
        e.height = target.h - ah
        e.x = target.x + e.pivotX * target.w - (ax0 + e.pivotX * aw)
        e.y = target.y + e.pivotY * target.h - (ay0 + e.pivotY * ah)
    }

    /** Moves the pivot to ([px], [py]) (0..1 of the rect, clamped) keeping the element where it is on screen. */
    fun setPivotKeepingRect(e: UIElementComponent, parent: UiRectF, rect: UiRectF, px: Float, py: Float) {
        e.pivotX = px.coerceIn(0f, 1f)
        e.pivotY = py.coerceIn(0f, 1f)
        setRect(e, parent, rect)
    }

    /**
     * Moves one anchor corner to ([ax], [ay]) (0..1 of the parent, clamped) keeping the element where it is on
     * screen. A point anchor (min == max) moves as one. The other corner is pushed along so min never passes max.
     */
    fun setAnchorKeepingRect(e: UIElementComponent, parent: UiRectF, rect: UiRectF, handle: UiHandle, ax: Float, ay: Float) {
        val x = ax.coerceIn(0f, 1f)
        val y = ay.coerceIn(0f, 1f)
        val point = e.anchorMinX == e.anchorMaxX && e.anchorMinY == e.anchorMaxY
        when {
            point -> e.anchors(x, y, x, y)
            handle == UiHandle.ANCHOR_MIN -> e.anchors(x, y, maxOf(x, e.anchorMaxX), maxOf(y, e.anchorMaxY))
            handle == UiHandle.ANCHOR_MAX -> e.anchors(minOf(x, e.anchorMinX), minOf(y, e.anchorMinY), x, y)
            else -> return
        }
        setRect(e, parent, rect)
    }

    /** Screen position (canvas units) of the anchor corner for [handle] inside [parent]. */
    fun anchorPoint(e: UIElementComponent, parent: UiRectF, handle: UiHandle): Pair<Float, Float> =
        if (handle == UiHandle.ANCHOR_MAX) parent.x + e.anchorMaxX * parent.w to parent.y + e.anchorMaxY * parent.h
        else parent.x + e.anchorMinX * parent.w to parent.y + e.anchorMinY * parent.h

    /** Pivot position (canvas units) of an element resolved to [rect]. */
    fun pivotPoint(e: UIElementComponent, rect: UiRectF): Pair<Float, Float> =
        rect.x + e.pivotX * rect.w to rect.y + e.pivotY * rect.h

    /**
     * Rect the element's anchors refer to: its parent element's rect, or the whole canvas for a direct child of a
     * canvas without its own rect (and for the canvas object itself). Null outside a canvas.
     */
    fun parentRect(go: GameObject): UiRectF? {
        val canvasGo = canvasOf(go) ?: return null
        val canvas = canvasGo.getComponent<UICanvasComponent>() ?: return null
        val full = UiRectF(0f, 0f, canvas.width, canvas.height)
        if (go === canvasGo) return full
        return go.parent?.getComponent<UIElementComponent>()?.rect ?: full
    }

    /** True when the parent's layout group places [go], so anchors and offsets are ignored and only the size counts. */
    fun isLayoutManaged(go: GameObject): Boolean {
        if (canvasOf(go) === go) return false
        val kind = go.parent?.getComponent<UIElementComponent>()?.layout?.kind ?: return false
        return kind != UiLayoutKind.NONE
    }

    /** Handle under ([px], [py]) for [rect], with [tolerance] around the edges; inside the rect is [UiHandle.MOVE]. */
    fun hitHandle(rect: UiRectF, px: Float, py: Float, tolerance: Float): UiHandle? {
        if (px < rect.x - tolerance || px > rect.x + rect.w + tolerance || py < rect.y - tolerance || py > rect.y + rect.h + tolerance) return null
        val west = abs(px - rect.x) <= tolerance
        val east = abs(px - (rect.x + rect.w)) <= tolerance
        val north = abs(py - rect.y) <= tolerance
        val south = abs(py - (rect.y + rect.h)) <= tolerance
        return when {
            north && west -> UiHandle.NW
            north && east -> UiHandle.NE
            south && west -> UiHandle.SW
            south && east -> UiHandle.SE
            north -> UiHandle.N
            south -> UiHandle.S
            west -> UiHandle.W
            east -> UiHandle.E
            else -> UiHandle.MOVE
        }
    }

    /** Canvas ancestor of [go] (or [go] itself), if any. */
    fun canvasOf(go: GameObject): GameObject? {
        var cur: GameObject? = go
        while (cur != null) {
            if (cur.getComponent<UICanvasComponent>() != null) return cur
            cur = cur.parent
        }
        return null
    }

    /** Topmost visible element of [canvas] containing the point; the canvas root counts only when it has a widget. */
    fun pick(canvas: GameObject, x: Float, y: Float): GameObject? =
        UiTree.elements(canvas)
            .filter { it.go !== canvas || UiRequirements.hasWidget(canvas) }
            .lastOrNull { it.element.rect.let { r -> x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h } }?.go
}

enum class UiKind { CANVAS, PANEL, TEXT, IMAGE, BUTTON, SLIDER, PROGRESS_BAR, LIST }

/** Creates ready-to-use UI GameObjects for the editor's Create menus. Text defaults are placeholder keys. */
object UiFactory {
    const val DEFAULT_TEXT_KEY = "ui.default.text"
    const val DEFAULT_BUTTON_KEY = "ui.default.button"

    fun create(kind: UiKind): GameObject = when (kind) {
        UiKind.CANVAS -> GameObject("Canvas").apply {
            addComponent(UICanvasComponent())
        }
        UiKind.PANEL -> element("Panel", 400f, 300f).apply { addComponent(UIImageComponent(tint = org.joml.Vector4f(0.1f, 0.1f, 0.14f, 0.9f))) }
        UiKind.TEXT -> element("Text", 300f, 48f).apply { addComponent(UITextComponent(key = DEFAULT_TEXT_KEY)) }
        UiKind.IMAGE -> element("Image", 200f, 200f).apply { addComponent(UIImageComponent()) }
        UiKind.BUTTON -> element("Button", 300f, 80f).apply { addComponent(UIButtonComponent(labelKey = DEFAULT_BUTTON_KEY)) }
        UiKind.SLIDER -> element("Slider", 400f, 40f).apply { addComponent(UISliderComponent(0f, 1f, 0.5f, 0f)) }
        UiKind.PROGRESS_BAR -> element("Progress Bar", 400f, 30f).apply { addComponent(UIProgressBarComponent(value = 0.5f)) }
        UiKind.LIST -> element("List", 400f, 240f).apply {
            addComponent(UIListComponent(items = listOf(UiListItem(DEFAULT_TEXT_KEY), UiListItem(DEFAULT_TEXT_KEY))))
        }
    }

    /** Whether [go] is a canvas or an element a UI tree can contain. */
    fun isUi(go: GameObject) = go.getComponent<UICanvasComponent>() != null || go.getComponent<UIElementComponent>() != null

    private fun element(name: String, w: Float, h: Float) = GameObject(name).apply {
        addComponent(UIElementComponent().placed(0.5f, 0.5f, 0f, 0f, w, h))
    }
}

/** Anchor presets offered by the editor. Point presets keep the current size; STRETCH fills the parent. */
enum class UiAnchorPreset(val ax: Float, val ay: Float, val stretch: Boolean = false) {
    TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
    MIDDLE_LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), MIDDLE_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f),
    STRETCH(0.5f, 0.5f, stretch = true);

    fun apply(e: UIElementComponent) {
        if (stretch) {
            e.stretched()
        } else {
            e.placed(ax, ay, 0f, 0f, if (e.anchorMinX != e.anchorMaxX) e.rect.w else e.width, if (e.anchorMinY != e.anchorMaxY) e.rect.h else e.height)
        }
    }
}
