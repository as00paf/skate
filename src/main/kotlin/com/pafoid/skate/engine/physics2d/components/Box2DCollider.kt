package com.pafoid.skate.engine.physics2d.components

import com.pafoid.skate.engine.scenes.components.Component
import org.joml.Vector2f

class Box2DCollider : Component() {
    val halfSize = Vector2f(1f)
    val offset: Vector2f = Vector2f()

    override fun editorUpdate(dt: Float) {
        // TODO: Implement DebugDraw for quads/boxes
    }
}