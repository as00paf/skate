package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.getComponent

/** What still keeps a hand-built UI object from showing up. */
enum class UiProblem { NO_CANVAS, NO_ELEMENT }

/**
 * Dependencies between UI components, so a widget added by hand (e.g. from the inspector's Add Component) is
 * visible right away: every widget needs a [UIElementComponent] rect and a canvas on itself or an ancestor.
 */
object UiRequirements {

    /** Components that draw or react to input and therefore need a rect. */
    fun isWidget(c: Component) = c is UIButtonComponent || c is UITextComponent || c is UIImageComponent ||
        c is UISliderComponent || c is UIProgressBarComponent || c is UIListComponent

    fun hasWidget(go: GameObject) = go.components.any { isWidget(it) }

    /** Default rect size for a widget, matching the editor's Create > UI menu. */
    fun defaultSize(c: Component?): Pair<Float, Float> = when (c) {
        is UIButtonComponent -> 300f to 80f
        is UITextComponent -> 300f to 48f
        is UISliderComponent -> 400f to 40f
        is UIProgressBarComponent -> 400f to 30f
        is UIListComponent -> 400f to 240f
        else -> 200f to 200f
    }

    fun defaultElement(widget: Component?): UIElementComponent {
        val (w, h) = defaultSize(widget)
        return UIElementComponent().placed(0.5f, 0.5f, 0f, 0f, w, h)
    }

    /** First unmet requirement of [go]'s UI components, or null when it renders as is. */
    fun problem(go: GameObject): UiProblem? {
        val isUi = hasWidget(go) || go.getComponent<UIElementComponent>() != null
        if (!isUi) return null
        if (hasWidget(go) && go.getComponent<UIElementComponent>() == null) return UiProblem.NO_ELEMENT
        if (UiEditing.canvasOf(go) == null) return UiProblem.NO_CANVAS
        return null
    }

    /**
     * Adds whatever [go] needs after [added] was attached: a centered rect sized for the widget, and a canvas on
     * [go] itself when no ancestor has one. Fresh text/button components get placeholder keys so they are
     * visible. Returns the components it added, so callers can undo them.
     */
    fun ensure(go: GameObject, added: Component? = null): List<Component> {
        val out = ArrayList<Component>()
        when (added) {
            is UIButtonComponent -> if (added.labelKey.isEmpty()) added.labelKey = UiFactory.DEFAULT_BUTTON_KEY
            is UITextComponent -> if (added.key.isEmpty()) added.key = UiFactory.DEFAULT_TEXT_KEY
            else -> Unit
        }
        if (hasWidget(go) && go.getComponent<UIElementComponent>() == null) {
            val widget = added?.takeIf { isWidget(it) } ?: go.components.first { isWidget(it) }
            val e = defaultElement(widget)
            go.addComponent(e)
            out.add(e)
        }
        val isUi = go.getComponent<UIElementComponent>() != null
        if (isUi && UiEditing.canvasOf(go) == null) {
            val c = UICanvasComponent()
            go.addComponent(c)
            out.add(c)
        }
        return out
    }

    /** Gives every widget of [canvasGo]'s tree (root included) a rect; repairs scenes built before [ensure] existed. */
    fun repairTree(canvasGo: GameObject) {
        fun visit(go: GameObject) {
            if (hasWidget(go) && go.getComponent<UIElementComponent>() == null) {
                go.addComponent(defaultElement(go.components.first { isWidget(it) }))
            }
            for (c in go.children) if (!c.isDead && c.getComponent<UICanvasComponent>() == null) visit(c)
        }
        visit(canvasGo)
    }
}
