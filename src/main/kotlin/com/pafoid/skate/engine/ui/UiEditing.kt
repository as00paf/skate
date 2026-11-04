package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.getComponent
import kotlin.math.abs

/** Which part of an element rect a viewport drag grabs. */
enum class UiHandle(val west: Boolean = false, val east: Boolean = false, val north: Boolean = false, val south: Boolean = false) {
    MOVE,
    N(north = true), S(south = true), W(west = true), E(east = true),
    NW(west = true, north = true), NE(east = true, north = true), SW(west = true, south = true), SE(east = true, south = true),
}

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

    /** Topmost visible element of [canvas] containing the point, ignoring the canvas root itself. */
    fun pick(canvas: GameObject, x: Float, y: Float): GameObject? =
        UiTree.elements(canvas).lastOrNull { it.element.rect.let { r -> x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h } }?.go
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
