package com.pafoid.skate.engine.animation.graph

import com.pafoid.skate.engine.animation.retarget.HumanoidBone
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton

/** Per-bone layer weights in [0,1], indexed by bone index. */
class BoneMask(val weights: FloatArray) {
    fun weight(bone: Int): Float = weights.getOrElse(bone) { 0f }

    companion object {
        fun all(boneCount: Int, value: Float = 1f) = BoneMask(FloatArray(boneCount) { value })

        /** Weight 1 for [rootBone] and all of its descendants (matched by name or humanoid slot), 0 elsewhere. */
        fun subtree(skeleton: Skeleton, rootBone: String): BoneMask {
            val w = FloatArray(skeleton.boneCount)
            val slot = HumanoidBone.fromName(rootBone)
            val start = skeleton.getAllBones().firstOrNull { it.name == rootBone }
                ?: skeleton.getAllBones().firstOrNull { slot != null && HumanoidBone.fromName(it.name) == slot }
            fun mark(b: Bone) { if (b.index in w.indices) w[b.index] = 1f; b.children.forEach(::mark) }
            start?.let(::mark)
            return BoneMask(w)
        }

        /** Everything from the first spine bone upward (spine, arms, neck, head); legs and hips stay at 0. */
        fun upperBody(skeleton: Skeleton): BoneMask = subtree(skeleton, "Spine")

        fun invert(mask: BoneMask) = BoneMask(FloatArray(mask.weights.size) { 1f - mask.weights[it] })
    }
}
