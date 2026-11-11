package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIMapComponent
import com.pafoid.skate.engine.ui.UIMapMarkerComponent
import com.pafoid.skate.engine.ui.UiRequirements
import com.pafoid.skate.engine.ui.UiTree

/**
 * Viewport picking of runtime UI elements (EDT-81). UI is drawn over the 3D scene and is not in the object-id buffer,
 * so a click is hit-tested against the canvases first, top-most first, and only reaches the 3D pick when no element is
 * under the pointer.
 */
object UiViewportPick {

    /** Enabled canvases of [scene] in hierarchy (pre-)order, the order the UI render system sees them in. */
    fun canvases(scene: Scene): List<GameObject> {
        val out = ArrayList<GameObject>()
        fun walk(node: GameObject) {
            for (child in node.children) {
                if (!child.isEnabled || child.isDead) continue
                if (child.getComponent<UICanvasComponent>() != null) out.add(child)
                walk(child)
            }
        }
        walk(scene)
        return out
    }

    /**
     * [canvases] from the one drawn last to the one drawn first: canvases draw in ascending sortOrder (stable, so
     * hierarchy order breaks ties), and the last one drawn is on top.
     */
    fun topFirst(canvases: List<GameObject>): List<GameObject> =
        canvases.sortedBy { it.getComponent<UICanvasComponent>()?.sortOrder ?: 0 }.asReversed()

    /**
     * The top-most visible UI element under ([x], [y]), given in logical pixels from the viewport image's top-left
     * corner; [dpiScale] is the framebuffer pixels per logical pixel. Null when the point is over no element, so the
     * click falls through to the 3D scene. Only elements that draw something catch a click ([catchesClicks]).
     */
    fun pick(canvases: List<GameObject>, x: Float, y: Float, dpiScale: Float): GameObject? {
        for (canvasGo in topFirst(canvases)) {
            val canvas = canvasGo.getComponent<UICanvasComponent>() ?: continue
            if (!canvas.visible || canvas.scale <= 0f) continue
            val k = canvas.scale / dpiScale
            val cx = x / k
            val cy = y / k
            // Draw order: the last element containing the point is on top.
            UiTree.elements(canvasGo).lastOrNull { n ->
                val r = n.element.rect
                catchesClicks(n.go) && cx >= r.x && cx <= r.x + r.w && cy >= r.y && cy <= r.y + r.h
            }?.let { return it.go }
        }
        return null
    }

    /**
     * Whether [go] draws something (a widget, a map or a map marker). Empty containers, such as full-screen layout
     * groups or a canvas root, are invisible: they must not swallow clicks meant for the 3D scene.
     */
    fun catchesClicks(go: GameObject) = UiRequirements.hasWidget(go) ||
        go.getComponent<UIMapComponent>() != null || go.getComponent<UIMapMarkerComponent>() != null

    /**
     * Whether a click may select a UI element: always with the Select tool, and with any tool while a UI element is
     * already selected (clicking another one switches to it, as the rect gizmo always did).
     */
    fun picksOnClick(selectToolActive: Boolean, uiSelected: Boolean) = selectToolActive || uiSelected
}
