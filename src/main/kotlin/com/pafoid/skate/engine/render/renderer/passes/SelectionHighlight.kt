package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene

/**
 * Highlight state uploaded as `SELECTED` for each draw. An object is tinted when it or any ancestor below
 * the [Scene] is selected or hovered, so a physics root without a mesh of its own (the skateboard) lights up
 * its visual children. When a child is selected directly, only that child's subtree is tinted.
 * Selection takes precedence over hover.
 */
object SelectionHighlight {
    const val NONE = 0.0f
    const val SELECTED = 1.0f
    const val HOVERED = 2.0f

    fun stateOf(go: GameObject, scene: Scene): Float {
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
