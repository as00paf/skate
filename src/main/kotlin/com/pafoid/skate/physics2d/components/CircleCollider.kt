package physics2d.components

import components.Component
import marki.renderer.DebugDraw
import org.joml.Vector2f

class CircleCollider(val offset:Vector2f = Vector2f()): Component() {
    var radius: Float = 1f

    override fun editorUpdate(dt: Float) {
        val center = Vector2f(gameObject.transform.position).add(offset)
        DebugDraw.addCircle(center, radius)
    }
}