package com.pafoid.skate.engine.animation.retarget

import com.pafoid.skate.engine.animation.edit.ClipEvaluator
import com.pafoid.skate.engine.animation.edit.ClipRig
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.AnimationChannel
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.AnimationSampler
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Retargeting with rest-pose compensation (ANI-16, ANI-12 §3.4). Unlike [Retargeter.retarget], which only renames
 * channels, this re-solves every mapped bone so it points the way the source bone points, whatever the two rigs' rest
 * poses (T-pose vs A-pose) and bone axis conventions are.
 *
 * Per mapped bone, in model space: `G_target(t) = G_source(t) * A_source⁻¹ * R_target_rest`, where `A_source` is the
 * source rest pose with each segment swung (parents first) to point like the matching target segment. So the source
 * playing its own rest pose drives the target into the *source's* rest shape, and the source posed like the target's
 * rest drives the target into its rest. Twist around a segment is carried over as is.
 *
 * Root motion: the hips' model-space displacement from rest, scaled by the hips-height ratio (or [translationScale]).
 * Output channels are LINEAR, keyed at every source key time, in the runtime channel space (`local = bind * TRS`).
 * Pure: no GL, no skeleton mutation.
 */
object RestPoseRetargeter {

    fun retarget(animation: Animation, source: Skeleton, target: Skeleton, translationScale: Float? = null): Animation {
        val src = ClipRig(source)
        val dst = ClipRig(target)
        val srcToDst = Retargeter.buildMap(src.names.toList(), target)
        val dstToSrc = HashMap<Int, Int>()
        for ((s, t) in srcToDst) dstToSrc.putIfAbsent(dst.indexOf(t), src.indexOf(s))

        // Clip channels may use another naming than the source skeleton (e.g. "Hips" vs "mixamorig:Hips").
        val channelToSource = Retargeter.buildMap(animation.channels.map { it.targetNodeName }.toSet(), source)
        val clip = SkAnimFormat.fromAnimation(animation).let { c ->
            c.copy(tracks = c.tracks.mapNotNull { tr -> channelToSource[tr.bone]?.let { tr.copy(bone = it) } })
        }
        val evaluator = ClipEvaluator(clip, src)

        val srcRest = globals(src, src.bindLocals)
        val dstRest = globals(dst, dst.bindLocals)
        val aligned = alignedSourceRest(src, dst, srcRest, dstRest, dstToSrc)
        val correction = HashMap<Int, Quaternionf>() // A_source⁻¹ * R_target_rest, per target bone
        for ((t, s) in dstToSrc) correction[t] = Quaternionf(aligned[s]).invert().mul(rotation(dstRest[t]))

        val hipsT = dst.names.indices.firstOrNull { HumanoidBone.fromName(dst.names[it]) == HumanoidBone.HIPS && it in dstToSrc }
        val scale = translationScale ?: hipsT?.let { t ->
            val h = srcRest[dstToSrc.getValue(t)].getTranslation(Vector3f()).y
            if (h > 1e-4f) dstRest[t].getTranslation(Vector3f()).y / h else 1f
        } ?: 1f
        val restScale = Array(dst.boneCount) { dstRest[it].getScale(Vector3f()) }

        val times = clip.tracks.flatMap { tr -> tr.keys.map { it.time } }.toSortedSet().ifEmpty { sortedSetOf(0f) }.toFloatArray()
        val driven = dst.names.indices.filter { it in dstToSrc }
        val rot = driven.associateWith { FloatArray(times.size * 4) }
        val pos = driven.associateWith { FloatArray(times.size * 3) }

        val dstGlobal = Array(dst.boneCount) { Matrix4f() }
        val order = hierarchyOrder(dst)
        for ((k, time) in times.withIndex()) {
            val srcGlobal = globals(src, evaluator.locals(time))
            for (t in order) {
                val parent = dst.parents[t]
                val parentG = if (parent >= 0) dstGlobal[parent] else IDENTITY
                val s = dstToSrc[t]
                if (s == null) { parentG.mul(dst.bindLocals[t], dstGlobal[t]); continue }

                val worldRot = rotation(srcGlobal[s]).mul(correction.getValue(t))
                val worldPos = if (t == hipsT) {
                    val d = srcGlobal[s].getTranslation(Vector3f()).sub(srcRest[s].getTranslation(Vector3f())).mul(scale)
                    dstRest[t].getTranslation(Vector3f()).add(d)
                } else parentG.transformPosition(dst.bindLocals[t].getTranslation(Vector3f()))
                dstGlobal[t].translationRotateScale(worldPos, worldRot, restScale[t])

                // Channel value = bind⁻¹ * local, local = parent⁻¹ * global.
                val channel = Matrix4f(dst.bindLocals[t]).invert().mul(Matrix4f(parentG).invert()).mul(dstGlobal[t])
                val c = channel.getTranslation(Vector3f())
                val q = channel.getUnnormalizedRotation(Quaternionf()).normalize()
                rot.getValue(t).let { a -> a[k * 4] = q.x; a[k * 4 + 1] = q.y; a[k * 4 + 2] = q.z; a[k * 4 + 3] = q.w }
                pos.getValue(t).let { a -> a[k * 3] = c.x; a[k * 3 + 1] = c.y; a[k * 3 + 2] = c.z }
            }
        }

        val channels = driven.flatMap { t ->
            val name = dst.names[t]
            listOf(
                AnimationChannel(AnimationSampler(times, pos.getValue(t), InterpolationType.LINEAR, 3), name, AnimationPath.TRANSLATION),
                AnimationChannel(AnimationSampler(times, continuous(rot.getValue(t)), InterpolationType.LINEAR, 4), name, AnimationPath.ROTATION),
                // Explicit unit scale: the runtime keeps the previous frame's value for missing channels (ANI-01 A2).
                AnimationChannel(AnimationSampler(floatArrayOf(0f), floatArrayOf(1f, 1f, 1f), InterpolationType.LINEAR, 3), name, AnimationPath.SCALE),
            )
        }
        return Animation(animation.name, channels, animation.duration, animation.path)
    }

    /** Source rest rotations (model space) with each mapped segment swung to point like its target segment. */
    private fun alignedSourceRest(
        src: ClipRig, dst: ClipRig, srcRest: Array<Matrix4f>, dstRest: Array<Matrix4f>, dstToSrc: Map<Int, Int>,
    ): Array<Quaternionf> {
        val srcToDst = dstToSrc.entries.associate { (t, s) -> s to t }
        val out = Array(src.boneCount) { Quaternionf() }
        for (s in hierarchyOrder(src)) {
            val parent = src.parents[s]
            val q = (if (parent >= 0) Quaternionf(out[parent]) else Quaternionf()).mul(rotation(src.bindLocals[s]))
            val t = srcToDst[s]
            val pair = t?.let { segmentPair(src, dst, s, it, srcToDst) }
            if (pair != null && HumanoidBone.fromName(dst.names[t]) != HumanoidBone.HIPS) {
                val (cs, ct) = pair
                // Rest offset to the child in the bone's own frame, re-expressed in the partially aligned pose.
                val restRot = rotation(srcRest[s])
                val local = Vector3f(srcRest[cs].getTranslation(Vector3f())).sub(srcRest[s].getTranslation(Vector3f()))
                restRot.conjugate().transform(local)
                val from = q.transform(Vector3f(local))
                val to = dstRest[ct].getTranslation(Vector3f()).sub(dstRest[t].getTranslation(Vector3f()))
                if (from.lengthSquared() > 1e-10f && to.lengthSquared() > 1e-10f) Quaternionf().rotationTo(from.normalize(), to.normalize()).mul(q, q)
            }
            out[s] = q
        }
        return out
    }

    /** A target child of [t] whose mapped source bone descends from [s]: the segment that defines the bone's direction. */
    private fun segmentPair(src: ClipRig, dst: ClipRig, s: Int, t: Int, srcToDst: Map<Int, Int>): Pair<Int, Int>? {
        val dstToSrc = srcToDst.entries.associate { (a, b) -> b to a }
        for (ct in dst.names.indices) {
            if (dst.parents[ct] != t) continue
            val cs = dstToSrc[ct] ?: continue
            if (isDescendant(src, cs, s)) return cs to ct
        }
        return null
    }

    private fun isDescendant(rig: ClipRig, bone: Int, ancestor: Int): Boolean {
        var i = rig.parents[bone]
        while (i >= 0) { if (i == ancestor) return true; i = rig.parents[i] }
        return false
    }

    private fun hierarchyOrder(rig: ClipRig): List<Int> {
        val depth = IntArray(rig.boneCount) { b -> var d = 0; var i = rig.parents[b]; while (i >= 0) { d++; i = rig.parents[i] }; d }
        return rig.names.indices.sortedBy { depth[it] }
    }

    private fun globals(rig: ClipRig, locals: Array<Matrix4f>): Array<Matrix4f> {
        val out = Array(rig.boneCount) { Matrix4f() }
        for (b in hierarchyOrder(rig)) {
            val p = rig.parents[b]
            if (p >= 0) out[p].mul(locals[b], out[b]) else out[b].set(locals[b])
        }
        return out
    }

    private fun rotation(m: Matrix4f) = m.getUnnormalizedRotation(Quaternionf()).normalize()

    private fun continuous(q: FloatArray): FloatArray {
        for (k in 1 until q.size / 4) {
            val dot = (0 until 4).sumOf { (q[(k - 1) * 4 + it] * q[k * 4 + it]).toDouble() }
            if (dot < 0) for (c in 0 until 4) q[k * 4 + c] = -q[k * 4 + c]
        }
        return q
    }

    private val IDENTITY = Matrix4f()
}
