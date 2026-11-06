package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component

/**
 * State of the single "Remove component" confirmation modal in the Properties window (EDT-49), kept free of ImGui
 * so it can be unit-tested.
 *
 * Only one removal can be pending. The modal is submitted once per frame at window level, not once per component,
 * so it can't be drawn twice or confirm the wrong component.
 */
class ComponentRemovalConfirmation {
    /** The component waiting for confirmation, or `null`. */
    var pending: Component? = null
        private set

    private var openRequested = false

    /** Trash button clicked: ask to confirm removing [component] (replaces any earlier request). */
    fun request(component: Component) {
        pending = component
        openRequested = true
    }

    /** Returns `true` once after [request]: the caller opens the popup this frame. */
    fun consumeOpenRequest(): Boolean {
        val result = openRequested
        openRequested = false
        return result
    }

    /**
     * Call once per frame, after a possible open, with the object the inspector shows and whether the popup is open.
     * Drops the request when the popup closed some other way, or when the component no longer belongs to [inspected].
     */
    fun sync(inspected: GameObject?, popupOpen: Boolean) {
        val target = pending ?: return
        if (openRequested) return
        if (!popupOpen || !isAttached(target, inspected)) cancel()
    }

    /**
     * Confirm clicked: returns the component to remove, or `null` if it is no longer on [inspected]
     * (undo, selection change), and clears the state.
     */
    fun confirm(inspected: GameObject?): Component? {
        val target = pending
        cancel()
        return target?.takeIf { isAttached(it, inspected) }
    }

    /** Cancel clicked, Esc, or the request became invalid. */
    fun cancel() {
        pending = null
        openRequested = false
    }

    // Identity check: GameObject.removeComponent does not clear Component.gameObject.
    private fun isAttached(component: Component, inspected: GameObject?): Boolean =
        inspected != null && inspected.components.any { it === component }
}
