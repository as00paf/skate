package com.pafoid.skate.engine.animation.graph

/**
 * Child weights of blend trees, shared by the runtime trees ([BlendTree1D], [BlendTree2D]) and the Animator window's
 * blend tree preview (ANI-23), so the editor shows exactly the mix the character plays. Pure.
 */
object BlendWeights {
    /**
     * Weights of the children of a 1D tree with [thresholds] (child order, need not be sorted) at [value]: the two
     * children around the value share the weight linearly; outside the range the nearest end child has it all.
     */
    fun blend1D(thresholds: FloatArray, value: Float): FloatArray {
        require(thresholds.isNotEmpty()) { "A 1D blend needs at least one threshold" }
        val order = thresholds.indices.sortedBy { thresholds[it] } // stable, like the runtime sort
        val w = FloatArray(thresholds.size)
        val (lo, hi, upper) = select1D(order.map { thresholds[it] }, value)
        w[order[lo]] += 1f - upper
        w[order[hi]] += upper
        return w
    }

    /** Over thresholds sorted ascending: (lower index, upper index, weight of the upper one). */
    internal fun select1D(sorted: List<Float>, value: Float): Triple<Int, Int, Float> {
        if (sorted.size == 1 || value <= sorted.first()) return Triple(0, 0, 0f)
        if (value >= sorted.last()) return Triple(sorted.size - 1, sorted.size - 1, 0f)
        val hi = sorted.indexOfFirst { it >= value }
        val lo = hi - 1
        val span = sorted[hi] - sorted[lo]
        return Triple(lo, hi, if (span <= 0f) 0f else (value - sorted[lo]) / span)
    }

    /** Normalized inverse-square-distance weights of 2D children at ([x], [y]); a child exactly there takes all. */
    fun blend2D(xs: FloatArray, ys: FloatArray, x: Float, y: Float): FloatArray {
        require(xs.isNotEmpty() && xs.size == ys.size) { "A 2D blend needs matching positions" }
        val w = FloatArray(xs.size)
        for (i in xs.indices) {
            val d = (xs[i] - x) * (xs[i] - x) + (ys[i] - y) * (ys[i] - y)
            if (d < 1e-8f) { w.fill(0f); w[i] = 1f; return w }
            w[i] = 1f / d
        }
        val sum = w.sum()
        for (i in w.indices) w[i] /= sum
        return w
    }

    /** Weights of the direct children of [def] for parameter values [x] (and [y] for 2D); null for a clip. */
    fun of(def: MotionDef, x: Float, y: Float = 0f): FloatArray? = when (def) {
        is ClipRef -> null
        is Blend1DDef -> blend1D(FloatArray(def.children.size) { def.children[it].threshold }, x)
        is Blend2DDef -> blend2D(FloatArray(def.children.size) { def.children[it].x }, FloatArray(def.children.size) { def.children[it].y }, x, y)
    }
}
