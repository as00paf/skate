package com.pafoid.skate.engine.animation.graph

import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton

/** Produces a pose for a normalized time in [0,1]. Implementations must not touch GL. */
interface Motion {
    /** Duration in seconds at speed 1 (blend trees blend their children's durations). */
    fun duration(params: AnimParameters): Float
    fun evaluate(params: AnimParameters, normalizedTime: Float, out: Pose)
}

/** Source of raw clip samples at an absolute time in seconds. */
fun interface ClipSource {
    fun sample(time: Float, out: Pose)
}

class ClipMotion(
    val name: String,
    private val clipDuration: Float,
    private val source: ClipSource,
) : Motion {
    override fun duration(params: AnimParameters) = clipDuration
    override fun evaluate(params: AnimParameters, normalizedTime: Float, out: Pose) {
        source.sample(normalizedTime.coerceIn(0f, 1f) * clipDuration, out)
    }

    companion object {
        /** Adapts an [Animation] by evaluating it on [skeleton] and snapshotting the local transforms. */
        fun of(animation: Animation, skeleton: Skeleton) = ClipMotion(animation.name, animation.duration) { time, out ->
            animation.update(time, skeleton)
            for (bone in skeleton.getAllBones()) if (bone.index < out.boneCount) out.setBone(bone.index, bone.localTransform)
        }
    }
}

/** 1D blend tree: children sorted by threshold, the adjacent pair around [parameter] is blended. */
class BlendTree1D(
    val parameter: String,
    children: List<Pair<Float, Motion>>,
) : Motion {
    private val children = children.sortedBy { it.first }

    init { require(children.isNotEmpty()) { "BlendTree1D needs at least one child" } }

    /** Returns (lowerIndex, upperIndex, weightOfUpper). */
    internal fun select(value: Float): Triple<Int, Int, Float> {
        if (children.size == 1 || value <= children.first().first) return Triple(0, 0, 0f)
        if (value >= children.last().first) return Triple(children.size - 1, children.size - 1, 0f)
        val hi = children.indexOfFirst { it.first >= value }
        val lo = hi - 1
        val span = children[hi].first - children[lo].first
        return Triple(lo, hi, if (span <= 0f) 0f else (value - children[lo].first) / span)
    }

    override fun duration(params: AnimParameters): Float {
        val (lo, hi, w) = select(params.getFloat(parameter))
        val a = children[lo].second.duration(params)
        return a + (children[hi].second.duration(params) - a) * w
    }

    override fun evaluate(params: AnimParameters, normalizedTime: Float, out: Pose) {
        val (lo, hi, w) = select(params.getFloat(parameter))
        children[lo].second.evaluate(params, normalizedTime, out)
        if (lo == hi || w <= 0f) return
        val tmp = Pose(out.boneCount)
        children[hi].second.evaluate(params, normalizedTime, tmp)
        Pose.blend(out, tmp, w, out)
    }
}

/** 2D blend tree using normalized inverse-distance weights over children positioned in (x, y) parameter space. */
class BlendTree2D(
    val parameterX: String,
    val parameterY: String,
    private val children: List<Triple<Float, Float, Motion>>,
) : Motion {
    init { require(children.isNotEmpty()) { "BlendTree2D needs at least one child" } }

    internal fun weights(x: Float, y: Float): FloatArray {
        val w = FloatArray(children.size)
        for ((i, c) in children.withIndex()) {
            val d = (c.first - x) * (c.first - x) + (c.second - y) * (c.second - y)
            if (d < 1e-8f) { w.fill(0f); w[i] = 1f; return w }
            w[i] = 1f / d
        }
        val sum = w.sum()
        for (i in w.indices) w[i] /= sum
        return w
    }

    override fun duration(params: AnimParameters): Float {
        val w = weights(params.getFloat(parameterX), params.getFloat(parameterY))
        return children.indices.sumOf { (w[it] * children[it].third.duration(params)).toDouble() }.toFloat()
    }

    override fun evaluate(params: AnimParameters, normalizedTime: Float, out: Pose) {
        val w = weights(params.getFloat(parameterX), params.getFloat(parameterY))
        var acc = 0f
        var first = true
        val tmp = Pose(out.boneCount)
        for ((i, c) in children.withIndex()) {
            if (w[i] <= 0f) continue
            if (first) {
                c.third.evaluate(params, normalizedTime, out)
                first = false
                acc = w[i]
            } else {
                c.third.evaluate(params, normalizedTime, tmp)
                acc += w[i]
                Pose.blend(out, tmp, w[i] / acc, out)
            }
        }
    }
}
