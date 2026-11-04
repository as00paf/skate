package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene

/**
 * Highlight state uploaded as `SELECTED` for each draw. An object is tinted when it or any ancestor below
 * the [Scene] is selected or hovered, so a physics root without a mesh of its own (the skateboard) lights up
 * its visual children. When a child is selected directly, only that child's subtree is tinted.
 * Selection takes precedence over hover. Nothing is tinted while the scene runs (play mode / exported game),
 * matching the grid, which the debug pass also hides then. The default shader tints selected objects at full
 * strength and hovered ones lighter.
 */
object SelectionHighlight {
    const val NONE = 0.0f
    const val SELECTED = 1.0f
    const val HOVERED = 2.0f

    fun stateOf(go: GameObject, scene: Scene): Float {
        if (scene.isRunning) return NONE
        val hovered = scene.hoveredGameObject
        var isHovered = false
        var node: GameObject? = go
        while (node != null && node !is Scene) {
            if (scene.isSelected(node)) return SELECTED
            if (node === hovered) isHovered = true
            node = node.parent
        }
        return if (isHovered) HOVERED else NONE
    }
}
