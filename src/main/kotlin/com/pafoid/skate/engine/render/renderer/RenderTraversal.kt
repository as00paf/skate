package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.ecs.GameObject

/**
 * The one scene walk shared by the geometry, shadow and picking passes (REN-20), so they agree on what is drawn.
 *
 * Visits every descendant of a root at any depth, so child models (a prefab's visual child) render, cast shadows
 * and pick like top-level ones. A hidden, disabled or dead object prunes its whole subtree: hiding a prefab root
 * hides its children everywhere, not only in the picking pass.
 */
object RenderTraversal {

    /** Whether [go] itself can be drawn (its ancestors are checked by the walk). */
    fun isRendered(go: GameObject): Boolean = go.isVisible && go.isEnabled && !go.isDead

    /** Calls [action] for every rendered descendant of [root] (not [root] itself), parents before children. */
    fun forEachRendered(root: GameObject, action: (GameObject) -> Unit) {
        for (go in root.children) {
            if (!isRendered(go)) continue
            action(go)
            forEachRendered(go, action)
        }
    }
}
