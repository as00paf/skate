package com.pafoid.skate.engine.animation.ik

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI

/** Applies an [IkRig] to a local pose. Pure: no scene or GL access. */
object IkPass {
    private val parentCache = java.util.WeakHashMap<Skeleton, IntArray>()

    fun parentsOf(skeleton: Skeleton): IntArray = synchronized(parentCache) {
        parentCache.getOrPut(skeleton) {
            IntArray(skeleton.boneCount) { -1 }.also { out ->
                fun walk(bone: Bone) {
                    for (child in bone.children) {
                        if (bone.index in out.indices && child.index in out.indices) out[child.index] = bone.index
                        walk(child)
                    }
                }
                walk(skeleton.rootBone)
            }
        }
    }

    /**
     * Frame entry point. When [sourceFresh] is false the locals still hold last frame's IK result, so the
     * saved pre-IK pose is restored first; this keeps partial weights from compounding on a static pose.
     */
    fun run(rig: IkRig, skeleton: Skeleton, locals: Array<Matrix4f>, worldToModel: Matrix4f?, sourceFresh: Boolean): Int {
        val saved = rig.basePose
        if (!sourceFresh && saved != null && saved.size == locals.size) {
            for (i in locals.indices) locals[i].set(saved[i])
        }
        if (!rig.isActive) {
            rig.basePose = null
            return 0
        }
        val base = saved?.takeIf { it.size == locals.size } ?: Array(locals.size) { Matrix4f() }
        for (i in locals.indices) base[i].set(locals[i])
        rig.basePose = base
        return apply(rig, skeleton, locals, worldToModel)
    }

    /**
     * Solves every active constraint in [rig], modifying [locals] in place.
     * [worldToModel] converts WORLD-space targets into the skeleton's model space (null = identity).
     * Returns the number of constraints that were solved.
     */
    fun apply(rig: IkRig, skeleton: Skeleton, locals: Array<Matrix4f>, worldToModel: Matrix4f? = null): Int {
        if (!rig.isActive) return 0
        val parents = parentsOf(skeleton)
        var solved = 0

        for (c in rig.twoBone) {
            if (c.weight <= 0f) continue
            val root = skeleton.getBoneByName(c.rootBone)?.index ?: continue
            val mid = skeleton.getBoneByName(c.midBone)?.index ?: continue
            val end = skeleton.getBoneByName(c.endBone)?.index ?: continue
            if (parents[mid] != root || parents[end] != mid) continue
            val target = toModel(c.target, c.space, worldToModel)
            val pole = c.pole?.let { toModel(it, c.space, worldToModel) }
            val rotation = c.endRotation?.let { toModel(it, c.space, worldToModel) }
            if (IkSolvers.solveTwoBone(parents, locals, root, mid, end, target, pole, c.weight, rotation, c.endRotationWeight)) solved++
        }

        for (c in rig.lookAt) {
            if (c.weight <= 0f) continue
            val bone = skeleton.getBoneByName(c.bone)?.index ?: continue
            val target = toModel(c.target, c.space, worldToModel)
            val maxAngle = Math.toRadians(c.maxAngleDegrees.toDouble()).toFloat().coerceIn(0f, PI.toFloat())
            if (IkSolvers.solveLookAt(parents, locals, bone, c.axis, target, c.weight, maxAngle)) solved++
        }
        return solved
    }

    private fun toModel(p: Vector3f, space: IkSpace, worldToModel: Matrix4f?): Vector3f =
        if (space == IkSpace.WORLD && worldToModel != null) worldToModel.transformPosition(Vector3f(p)) else Vector3f(p)

    private fun toModel(q: Quaternionf, space: IkSpace, worldToModel: Matrix4f?): Quaternionf =
        if (space == IkSpace.WORLD && worldToModel != null) {
            worldToModel.getUnnormalizedRotation(Quaternionf()).normalize().mul(q).normalize()
        } else Quaternionf(q)
}
