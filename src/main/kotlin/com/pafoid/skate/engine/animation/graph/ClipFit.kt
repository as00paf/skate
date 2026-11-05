package com.pafoid.skate.engine.animation.graph

import com.pafoid.skate.engine.animation.retarget.HumanoidBone
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import kotlinx.serialization.Serializable
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Per-clip placement fix applied when a controller is built, for clips authored on a stage or prop (e.g. a
 * skateboard clip whose feet sit on a deck the rig doesn't have).
 */
@Serializable
data class ClipFitDef(
    /**
     * Shift the hips so the lowest foot/toe joint of the clip's first frame is at its bind-pose height, i.e. the
     * feet land on the character's origin plane. Needs a humanoid rig (hips and feet).
     */
    val groundFeet: Boolean = false,
    /** Extra hips offset (x, y, z) in skeleton units, added after [groundFeet]. */
    val offset: List<Float> = listOf(0f, 0f, 0f),
) {
    init { require(offset.size == 3) { "Clip fit offset needs 3 components" } }
}

/** A motion whose hips translation is shifted by a constant [localShift] (in the hips' parent space). Pure. */
class FittedMotion(val inner: Motion, private val hips: Int, private val localShift: Vector3f) : Motion {
    override fun duration(params: AnimParameters) = inner.duration(params)

    override fun evaluate(params: AnimParameters, normalizedTime: Float, out: Pose) {
        inner.evaluate(params, normalizedTime, out)
        out.translations[hips * 3] += localShift.x
        out.translations[hips * 3 + 1] += localShift.y
        out.translations[hips * 3 + 2] += localShift.z
    }

    companion object {
        /** Wraps [motion] per [def] on [skeleton]; returns [motion] unchanged when there is nothing to do. */
        fun of(motion: Motion, def: ClipFitDef, skeleton: Skeleton): Motion {
            val rig = Rig(skeleton)
            val hips = rig.slot(HumanoidBone.HIPS) ?: throw IllegalArgumentException("Clip fit needs a hips bone")
            val shift = Vector3f(def.offset[0], def.offset[1], def.offset[2])
            if (def.groundFeet) {
                val feet = FEET.mapNotNull { rig.slot(it) }
                require(feet.isNotEmpty()) { "groundFeet needs foot bones" }
                val up = rig.up()
                val pose = Pose(skeleton.boneCount).also { motion.evaluate(AnimParameters(), 0f, it) }
                val posed = rig.globals { i, m -> pose.toMatrix(i, m) }
                val rest = rig.globals { i, m -> m.set(rig.bind[i]) }
                fun lowest(g: Array<Matrix4f>) = feet.minOf { g[it].getTranslation(Vector3f()).dot(up) }
                shift.add(Vector3f(up).mul(lowest(rest) - lowest(posed)))
            }
            if (shift.lengthSquared() == 0f) return motion
            // The shift is in model space; hips translation lives in its parent's space.
            val parent = rig.parents[hips]
            if (parent >= 0) {
                val parentRot = rig.globals { i, m -> m.set(rig.bind[i]) }[parent].getUnnormalizedRotation(Quaternionf()).normalize()
                parentRot.conjugate().transform(shift)
            }
            return FittedMotion(motion, hips, shift)
        }

        private val FEET = listOf(HumanoidBone.LEFT_FOOT, HumanoidBone.RIGHT_FOOT, HumanoidBone.LEFT_TOES, HumanoidBone.RIGHT_TOES)
    }

    private class Rig(skeleton: Skeleton) {
        val count = skeleton.boneCount
        val parents = IntArray(count) { -1 }
        val bind = Array(count) { Matrix4f() }
        private val slots = mutableMapOf<HumanoidBone, Int>()
        private val order = mutableListOf<Int>()

        init {
            fun walk(bone: Bone, parent: Int) {
                val valid = bone.index in 0 until count
                if (valid) {
                    parents[bone.index] = parent
                    bind[bone.index].set(bone.bindLocalTransform)
                    order += bone.index
                    HumanoidBone.fromName(bone.name)?.let { slots.putIfAbsent(it, bone.index) }
                }
                bone.children.forEach { walk(it, if (valid) bone.index else parent) }
            }
            walk(skeleton.rootBone, -1)
        }

        fun slot(bone: HumanoidBone) = slots[bone]

        /** Model-space matrices, parents first; [local] fills bone i's local matrix. */
        fun globals(local: (Int, Matrix4f) -> Unit): Array<Matrix4f> {
            val out = Array(count) { Matrix4f() }
            val tmp = Matrix4f()
            for (i in order) {
                local(i, tmp)
                val p = parents[i]
                if (p >= 0) out[p].mul(tmp, out[i]) else out[i].set(tmp)
            }
            return out
        }

        fun up(): Vector3f {
            val hips = slot(HumanoidBone.HIPS) ?: return Vector3f(0f, 1f, 0f)
            val head = slot(HumanoidBone.HEAD) ?: return Vector3f(0f, 1f, 0f)
            val g = globals { i, m -> m.set(bind[i]) }
            val v = g[head].getTranslation(Vector3f()).sub(g[hips].getTranslation(Vector3f()))
            return if (v.lengthSquared() < 1e-10f) Vector3f(0f, 1f, 0f) else v.normalize()
        }
    }
}
