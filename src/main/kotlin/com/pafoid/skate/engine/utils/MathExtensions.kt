package com.pafoid.skate.engine.utils

/**
 * Converts this float value from degrees to radians.
 */
fun Float.toRadians(): Float {
    return Math.toRadians(this.toDouble()).toFloat()
}

/**
 * Converts this float value from radians to degrees.
 */
fun Float.toDegrees(): Float {
    return Math.toDegrees(this.toDouble()).toFloat()
}

/**
 * Converts this double value from radians to degrees.
 */
fun Double.toDegrees(): Double {
    return Math.toDegrees(this)
}