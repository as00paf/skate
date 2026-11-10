package com.pafoid.skate.engine.animation.edit

/** Lets tests in other packages measure rotation differences with the edit package's helpers. */
object EditTestDataAccess {
    fun angle(a: List<Float>, b: List<Float>): Float = QuatMath.angleDeg(a.toFloatArray(), b.toFloatArray())
}
