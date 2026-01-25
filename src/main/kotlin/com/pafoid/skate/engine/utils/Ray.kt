package com.pafoid.skate.engine.utils

import org.joml.Vector3f

class Ray(val origin: Vector3f, val direction: Vector3f) {
    fun distanceToPoint(point: Vector3f): Float {
        val v = Vector3f(point).sub(origin)
        val t = v.dot(direction)
        if (t < 0) return origin.distance(point)
        val projection = Vector3f(origin).add(Vector3f(direction).mul(t))
        return projection.distance(point)
    }
}
