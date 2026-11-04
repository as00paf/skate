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
        val root = canvasGo.getComponent<UIElementComponent>()
        if (root == null) {
            collect(canvasGo, canvas, 1f, true, out)
        } else if (root.visible) {
            // The canvas object's own rect (and widgets on it) is the root of its tree.
            out.add(UiNodeRef(canvasGo, root, canvas, root.opacity, root.interactable))
            collect(canvasGo, canvas, root.opacity, root.interactable, out)
        }
        return out
    }

    /** A child with its own canvas is a separate tree, laid out and drawn by itself. */
    private fun GameObject.isNestedCanvas() = getComponent<UICanvasComponent>() != null

    private fun collect(parent: GameObject, canvas: UICanvasComponent, opacity: Float, interactable: Boolean, out: MutableList<UiNodeRef>) {
        for (child in parent.children.sortedBy { it.getComponent<UIElementComponent>()?.zOrder ?: 0 }) {
            if (!child.isEnabled || child.isDead || child.isNestedCanvas()) continue
            val e = child.getComponent<UIElementComponent>() ?: continue
            if (!e.visible) continue
            val o = opacity * e.opacity
            val i = interactable && e.interactable
            out.add(UiNodeRef(child, e, canvas, o, i))
            collect(child, canvas, o, i, out)
        }
    }

    /** Layout spec for the canvas subtree; hidden elements are still laid out so toggling visibility never shifts siblings. */
    fun spec(canvasGo: GameObject): UiNodeSpec =
        canvasGo.getComponent<UIElementComponent>()?.let { nodeSpec(canvasGo, it) }
            ?: UiNodeSpec(canvasGo.uId, children = childSpecs(canvasGo))

    /** Whether the canvas object carries its own rect, which is then anchored inside the screen instead of filling it. */
    fun hasRootElement(canvasGo: GameObject) = canvasGo.getComponent<UIElementComponent>() != null

    private fun nodeSpec(go: GameObject, e: UIElementComponent) = UiNodeSpec(
        go.uId,
        e.anchorMinX, e.anchorMinY, e.anchorMaxX, e.anchorMaxY,
        e.pivotX, e.pivotY, e.x, e.y, e.width, e.height,
        e.layout, childSpecs(go),
    )

    private fun childSpecs(parent: GameObject): List<UiNodeSpec> =
        parent.children.filter { !it.isDead && !it.isNestedCanvas() }.mapNotNull { child ->
            val e = child.getComponent<UIElementComponent>() ?: return@mapNotNull null
            nodeSpec(child, e)
        }

    /** Hash of everything that influences layout, so unchanged trees are not re-resolved. */
    fun layoutSignature(canvasGo: GameObject, screenW: Int, screenH: Int): Int {
        var h = 31 * screenW + screenH
        fun hash(c: GameObject, e: UIElementComponent) {
            h = 31 * h + c.uId
            // Identity too: a reloaded or restored element starts with an empty rect even when its values match.
            h = 31 * h + java.lang.System.identityHashCode(e)
            h = 31 * h + e.layout.hashCode()
            for (f in floatArrayOf(
                e.anchorMinX, e.anchorMinY, e.anchorMaxX, e.anchorMaxY, e.pivotX, e.pivotY, e.x, e.y, e.width, e.height,
            )) h = 31 * h + f.toRawBits()
        }
        fun visit(go: GameObject) {
            for (c in go.children) {
                if (c.isNestedCanvas()) continue
                val e = c.getComponent<UIElementComponent>() ?: continue
                hash(c, e)
                visit(c)
            }
        }
        canvasGo.getComponent<UIElementComponent>()?.let { hash(canvasGo, it) }
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
