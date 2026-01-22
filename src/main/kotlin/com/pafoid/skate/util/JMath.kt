package com.pafoid.skate.util

import org.joml.Vector2f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object JMath {

    fun rotate(vec: Vector2f, origin: Vector2f, angleDeg: Double) {
        val x = vec.x - origin.x
        val y = vec.y - origin.y
        val cos = cos(Math.toRadians(angleDeg))
        val sin = sin(Math.toRadians(angleDeg))

        var xPrime = (x * cos) - (y * sin)
        var yPrime = (x * sin) + (y * cos)

        xPrime += origin.x
        yPrime += origin.y

        vec.x = xPrime.toFloat()
        vec.y = yPrime.toFloat()
    }

    fun compare(x: Float, y: Float, epsilon: Float = Float.MIN_VALUE) : Boolean {
        return abs(x - y) <= epsilon * max(1f, max(abs(x), abs(y)))
    }

    fun compare(vec1: Vector2f, vec2: Vector2f, epsilon: Float = Float.MIN_VALUE): Boolean {
        return (compare(vec1.x, vec2.x, epsilon)) && compare(vec1.y, vec2.y, epsilon)
    }

}