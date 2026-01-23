package com.pafoid.skate.engine.utils

fun Float.toRadians(): Float {
    return Math.toRadians(this.toDouble()).toFloat()
}