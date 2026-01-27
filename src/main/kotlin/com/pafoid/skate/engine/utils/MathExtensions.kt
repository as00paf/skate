package com.pafoid.skate.engine.utils

fun Float.toRadians(): Float {
    return Math.toRadians(this.toDouble()).toFloat()
}

fun Float.toDegrees(): Float {
    return Math.toDegrees(this.toDouble()).toFloat()
}

fun Double.toDegrees(): Double {
    return Math.toDegrees(this)
}