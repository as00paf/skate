package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.getComponent

/** One drawable/interactive element with state inherited from its ancestors. */
class UiNodeRef(
    val go: GameObject,
    val element: UIElementComponent,
    val canvas: UICanvasComponent,
    /** Product of the opacities of the element and all its UI ancestors. */
    val opacity: Float,
    /** Whether the element and every UI ancestor are interactable. */
    val interactable: Boolean,
)

/** Reads the GameObject hierarchy under a canvas. */
object UiTree {
    /** Visible elements of [canvasGo] in draw order (parents first; siblings by zOrder, then hierarchy order). */
    fun elements(canvasGo: GameObject): List<UiNodeRef> {
        val canvas = canvasGo.getComponent<UICanvasComponent>() ?: return emptyList()
        if (!canvas.visible) return emptyList()
        val out = ArrayList<UiNodeRef>()
        collect(canvasGo, canvas, 1f, true, out)
        return out
    }

    private fun collect(parent: GameObject, canvas: UICanvasComponent, opacity: Float, interactable: Boolean, out: MutableList<UiNodeRef>) {
        for (child in parent.children.sortedBy { it.getComponent<UIElementComponent>()?.zOrder ?: 0 }) {
            if (!child.isEnabled || child.isDead) continue
            val e = child.getComponent<UIElementComponent>() ?: continue
            if (!e.visible) continue
            val o = opacity * e.opacity
            val i = interactable && e.interactable
            out.add(UiNodeRef(child, e, canvas, o, i))
            collect(child, canvas, o, i, out)
        }
    }

    /** Layout spec for the canvas subtree; hidden elements are still laid out so toggling visibility never shifts siblings. */
    fun spec(canvasGo: GameObject): UiNodeSpec = UiNodeSpec(canvasGo.uId, children = childSpecs(canvasGo))

    private fun childSpecs(parent: GameObject): List<UiNodeSpec> =
        parent.children.filter { !it.isDead }.mapNotNull { child ->
            val e = child.getComponent<UIElementComponent>() ?: return@mapNotNull null
            UiNodeSpec(
                child.uId,
                e.anchorMinX, e.anchorMinY, e.anchorMaxX, e.anchorMaxY,
                e.pivotX, e.pivotY, e.x, e.y, e.width, e.height,
                e.layout, childSpecs(child),
            )
        }

    /** Hash of everything that influences layout, so unchanged trees are not re-resolved. */
    fun layoutSignature(canvasGo: GameObject, screenW: Int, screenH: Int): Int {
        var h = 31 * screenW + screenH
        fun visit(go: GameObject) {
            for (c in go.children) {
                val e = c.getComponent<UIElementComponent>() ?: continue
                h = 31 * h + c.uId
                h = 31 * h + e.layout.hashCode()
                for (f in floatArrayOf(
                    e.anchorMinX, e.anchorMinY, e.anchorMaxX, e.anchorMaxY, e.pivotX, e.pivotY, e.x, e.y, e.width, e.height,
                )) h = 31 * h + f.toRawBits()
                visit(c)
            }
        }
        val canvas = canvasGo.getComponent<UICanvasComponent>()
        if (canvas != null) {
            h = 31 * h + canvas.scaleMode.ordinal
            h = 31 * h + canvas.referenceWidth.toRawBits()
            h = 31 * h + canvas.referenceHeight.toRawBits()
            h = 31 * h + canvas.match.toRawBits()
        }
        visit(canvasGo)
        return h
    }
}
