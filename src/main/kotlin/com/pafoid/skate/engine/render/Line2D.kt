package com.pafoid.skate.engine.render

import org.joml.Vector2f
import org.joml.Vector3f

class Line2D(
    val from: Vector2f = Vector2f(),
    val to: Vector2f = Vector2f(),
    val color: Vector3f = Vector3f(),
    var lifetime: Int = 1
) {
    fun beginFrame(): Int {
        lifetime--
        return lifetime
    }
}