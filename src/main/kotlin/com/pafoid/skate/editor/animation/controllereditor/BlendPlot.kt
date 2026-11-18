package com.pafoid.skate.editor.animation.controllereditor

/** Axis ranges of the blend tree preview in the Animator window (ANI-23). Pure. */
object BlendPlot {
    /** A range covering [values] with a 10% margin; a single value gets ±1 around it. */
    fun bounds(values: FloatArray): Pair<Float, Float> {
        val lo = values.minOrNull() ?: 0f
        val hi = values.maxOrNull() ?: 0f
        if (hi - lo < 1e-4f) return (lo - 1f) to (hi + 1f)
        val m = (hi - lo) * 0.1f
        return (lo - m) to (hi + m)
    }
}
