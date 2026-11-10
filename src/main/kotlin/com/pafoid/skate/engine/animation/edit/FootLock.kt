package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.ik.IkSolvers
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.roundToInt

/** The three bones of a leg, by name (thigh, shin, foot). */
data class LegChain(val upperLeg: String, val lowerLeg: String, val foot: String) {
    companion object {
        val LEFT = LegChain("LeftUpLeg", "LeftLeg", "LeftFoot")
        val RIGHT = LegChain("RightUpLeg", "RightLeg", "RightFoot")
    }
}

/**
 * Plants a foot over a time range: the foot keeps the model-space position (and, optionally, orientation) it has at
 * [start] while the hips and body keep moving; the leg is re-solved with two-bone IK at every frame (clip fps).
 * [blendSeconds] eases the lock in before [start] and out after [end] so the range edges do not pop.
 * The leg's rotation keys inside the processed range are replaced with the solved ones.
 */
object FootLock {
    fun apply(
        clip: SkClip,
        skeleton: Skeleton,
        leg: LegChain,
        start: Float,
        end: Float,
        blendSeconds: Float = 0f,
        lockRotation: Boolean = true,
    ): SkClip {
        require(end >= start) { "Foot lock range is reversed" }
        val rig = ClipRig(skeleton)
        val upper = rig.indexOf(leg.upperLeg)
        val lower = rig.indexOf(leg.lowerLeg)
        val foot = rig.indexOf(leg.foot)
        val evaluator = ClipEvaluator(clip, rig)

        val planted = IkSolvers.globalOf(rig.parents, evaluator.locals(start.coerceIn(0f, clip.duration)), foot)
        val target = planted.getTranslation(Vector3f())
        val targetRotation = if (lockRotation) planted.getUnnormalizedRotation(Quaternionf()).normalize() else null

        val from = (start - blendSeconds).coerceAtLeast(0f)
        val to = (end + blendSeconds).coerceAtMost(clip.duration)
        val solved = mapOf(upper to ArrayList<Pair<Float, FloatArray>>(), lower to ArrayList(), foot to ArrayList())
        for (t in frameTimes(from, to, start, end, clip.fps)) {
            val locals = evaluator.locals(t)
            IkSolvers.solveTwoBone(rig.parents, locals, upper, lower, foot, target, weight = weight(t, start, end, blendSeconds), endRotation = targetRotation)
            for ((bone, keys) in solved) keys += t to QuatMath.toRow(evaluator.channelRotation(bone, locals[bone]))
        }

        var out = clip
        for ((bone, keys) in solved) {
            val name = rig.names[bone]
            val existing = out.track(name, AnimationPath.ROTATION) ?: SkTrack(name, AnimationPath.ROTATION, InterpolationType.LINEAR)
            var track = existing.copy(keys = existing.keys.filter { it.time < from - KeyEditing.TIME_EPSILON || it.time > to + KeyEditing.TIME_EPSILON })
            for ((t, q) in keys) track = KeyEditing.insertKey(track, t, q)
            out = out.withTrack(track)
        }
        return out
    }

    private fun weight(t: Float, start: Float, end: Float, blend: Float): Float = when {
        t in start..end -> 1f
        blend <= 0f -> 0f
        t < start -> smoothstep(1f - (start - t) / blend)
        else -> smoothstep(1f - (t - end) / blend)
    }

    private fun smoothstep(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

    private fun frameTimes(from: Float, to: Float, start: Float, end: Float, fps: Float): List<Float> {
        val frames = ((to - from) * fps).roundToInt().coerceAtLeast(0)
        val grid = (0..frames).map { (from + it / fps).coerceAtMost(to) } + listOf(start, end, to)
        return grid.filter { it in from..to }.sorted().fold(ArrayList()) { acc, t ->
            if (acc.isEmpty() || abs(acc.last() - t) > KeyEditing.TIME_EPSILON) acc.add(t)
            acc
        }
    }
}
