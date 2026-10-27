package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.ecs.systems.SystemPhase
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.ui.GridFont
import com.pafoid.skate.engine.render.ui.NineSliceInsets
import com.pafoid.skate.engine.render.ui.UiDrawList
import com.pafoid.skate.engine.render.ui.drawText
import org.joml.Vector4f
import kotlin.math.roundToInt

/**
 * Records every visible canvas into the [UiDrawList] returned by [drawList] (screen pixels, origin top-left).
 * Canvases draw in ascending sortOrder; the renderer submits the list after the 3D scene.
 */
class UiRenderSystem(
    private val drawList: () -> UiDrawList?,
    private val strings: StringManager,
    private val font: () -> GridFont?,
    private val textures: (String) -> Texture?,
) : System(priority = ExecutionPriority.LATE) {

    override val phase = SystemPhase.RENDER

    private val canvases = query(UICanvasComponent::class)

    override fun render(alpha: Float) {
        val list = drawList() ?: return
        draw(list, canvases.entities)
    }

    fun draw(list: UiDrawList, canvasObjects: List<GameObject>) {
        for (canvasGo in canvasObjects.sortedBy { it.getComponent<UICanvasComponent>()?.sortOrder ?: 0 }) {
            for (node in UiTree.elements(canvasGo)) drawNode(list, node)
        }
    }

    private fun drawNode(list: UiDrawList, n: UiNodeRef) {
        val s = n.canvas.scale
        val r = n.element.rect
        val x = (r.x * s).roundToInt().toFloat()
        val y = (r.y * s).roundToInt().toFloat()
        val w = (r.w * s).roundToInt().toFloat()
        val h = (r.h * s).roundToInt().toFloat()
        val go = n.go
        val o = n.opacity

        go.getComponent<UIImageComponent>()?.let { drawImage(list, it, x, y, w, h, s, o) }
        go.getComponent<UIProgressBarComponent>()?.let { drawProgress(list, it, x, y, w, h, o) }
        go.getComponent<UISliderComponent>()?.let { drawSlider(list, it, x, y, w, h, o) }
        go.getComponent<UIButtonComponent>()?.let { drawButton(list, it, n, x, y, w, h, s) }
        go.getComponent<UIListComponent>()?.let { drawRows(list, it, x, y, w, h, s, o) }
        go.getComponent<UITextComponent>()?.let { t ->
            if (t.key.isNotEmpty()) {
                label(list, resolve(t.key, t.args), x, y, w, h, t.fontSize * s, t.alignX, t.alignY, t.color, o)
            }
        }
        if (n.element.focused) outline(list, x, y, w, h, 3f * s, focusColorOf(go), o)
    }

    private fun focusColorOf(go: GameObject): Vector4f = go.getComponent<UIButtonComponent>()?.focusColor ?: FOCUS_DEFAULT

    private fun resolve(key: String, args: List<String>): String =
        if (args.isEmpty()) strings.getString(key) else strings.getString(key, *args.toTypedArray())

    private fun drawImage(list: UiDrawList, img: UIImageComponent, x: Float, y: Float, w: Float, h: Float, scale: Float, o: Float) {
        val path = img.texturePath
        val tex = img.texture ?: path?.takeIf { it.isNotBlank() }?.let(textures)?.also { img.texture = it }
        val c = img.tint
        val sliced = img.sliceLeft > 0 || img.sliceTop > 0 || img.sliceRight > 0 || img.sliceBottom > 0
        if (tex != null && sliced) {
            list.drawNineSlice(
                x, y, w, h, tex, NineSliceInsets(img.sliceLeft, img.sliceTop, img.sliceRight, img.sliceBottom), scale,
                c.x, c.y, c.z, c.w * o,
            )
        } else {
            list.drawQuad(x, y, w, h, c.x, c.y, c.z, c.w * o, tex)
        }
    }

    private fun drawProgress(list: UiDrawList, p: UIProgressBarComponent, x: Float, y: Float, w: Float, h: Float, o: Float) {
        quad(list, x, y, w, h, p.backgroundColor, o)
        fill(list, p.value.coerceIn(0f, 1f), p.direction, x, y, w, h, p.fillColor, o)
    }

    private fun drawSlider(list: UiDrawList, s: UISliderComponent, x: Float, y: Float, w: Float, h: Float, o: Float) {
        quad(list, x, y, w, h, s.trackColor, o)
        fill(list, UiWidgetMath.fraction(s.value, s.min, s.max), UiFillDirection.LEFT_TO_RIGHT, x, y, w, h, s.fillColor, o)
    }

    private fun fill(list: UiDrawList, f: Float, dir: UiFillDirection, x: Float, y: Float, w: Float, h: Float, c: Vector4f, o: Float) {
        when (dir) {
            UiFillDirection.LEFT_TO_RIGHT -> quad(list, x, y, w * f, h, c, o)
            UiFillDirection.RIGHT_TO_LEFT -> quad(list, x + w * (1f - f), y, w * f, h, c, o)
            UiFillDirection.TOP_TO_BOTTOM -> quad(list, x, y, w, h * f, c, o)
            UiFillDirection.BOTTOM_TO_TOP -> quad(list, x, y + h * (1f - f), w, h * f, c, o)
        }
    }

    private fun drawButton(list: UiDrawList, b: UIButtonComponent, n: UiNodeRef, x: Float, y: Float, w: Float, h: Float, scale: Float) {
        val bg = when {
            !n.interactable -> b.disabledColor
            b.pressed -> b.pressedColor
            b.hovered || n.element.focused -> b.hoverColor
            else -> b.normalColor
        }
        quad(list, x, y, w, h, bg, n.opacity)
        if (b.labelKey.isNotEmpty()) label(list, strings.getString(b.labelKey), x, y, w, h, b.fontSize * scale, 0.5f, 0.5f, b.labelColor, n.opacity)
    }

    private fun drawRows(list: UiDrawList, l: UIListComponent, x: Float, y: Float, w: Float, h: Float, scale: Float, o: Float) {
        list.pushScissor(x.toInt(), y.toInt(), w.toInt(), h.toInt())
        val rowH = l.itemHeight * scale
        for (i in UiWidgetMath.visibleItems(l.items.size, l.itemHeight, h / scale, l.scroll)) {
            val ry = y + (i * l.itemHeight - l.scroll) * scale
            if (i == l.selectedIndex) quad(list, x, ry, w, rowH, l.selectionColor, o)
            val item = l.items[i]
            label(list, resolve(item.key, item.args), x + 8f * scale, ry, w - 16f * scale, rowH, rowH * 0.7f, 0f, 0.5f, l.textColor, o)
        }
        list.popScissor()
    }

    private fun quad(list: UiDrawList, x: Float, y: Float, w: Float, h: Float, c: Vector4f, o: Float) =
        list.drawQuad(x, y, w, h, c.x, c.y, c.z, c.w * o)

    private fun outline(list: UiDrawList, x: Float, y: Float, w: Float, h: Float, t: Float, c: Vector4f, o: Float) {
        quad(list, x, y, w, t, c, o)
        quad(list, x, y + h - t, w, t, c, o)
        quad(list, x, y + t, t, h - 2 * t, c, o)
        quad(list, x + w - t, y + t, t, h - 2 * t, c, o)
    }

    private fun label(
        list: UiDrawList, text: String, x: Float, y: Float, w: Float, h: Float,
        cellH: Float, alignX: Float, alignY: Float, c: Vector4f, o: Float,
    ) {
        val f = font() ?: return
        if (text.isEmpty() || cellH <= 0f) return
        val cellW = cellH * f.cellAspect
        val px = (x + (w - text.length * cellW) * alignX).roundToInt().toFloat()
        val py = (y + (h - cellH) * alignY).roundToInt().toFloat()
        list.pushScissor(x.toInt(), y.toInt(), w.toInt(), h.toInt())
        list.drawText(f, text, px, py, cellW, cellH, c.x, c.y, c.z, c.w * o)
        list.popScissor()
    }

    private companion object {
        val FOCUS_DEFAULT = Vector4f(1f, 0.8f, 0.2f, 1f)
    }
}
