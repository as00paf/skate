package com.pafoid.skate.engine.animation.graph

import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import kotlinx.serialization.Serializable

enum class MaskPreset { FULL_BODY, UPPER_BODY, LOWER_BODY, SUBTREE }

/**
 * Which bones an Animator's controller drives (ANI-03 masks). Bones outside the mask keep the base pose: the clip
 * picked in the inspector while it plays, else the bind pose. Saved with the scene.
 */
@Serializable
data class AnimatorMaskDef(
    val preset: MaskPreset = MaskPreset.FULL_BODY,
    /** Root of the masked subtree for [MaskPreset.SUBTREE] (bone name or humanoid name such as "LeftArm"). */
    val rootBone: String = "",
) {
    val isFullBody: Boolean get() = preset == MaskPreset.FULL_BODY || (preset == MaskPreset.SUBTREE && rootBone.isBlank())

    /** The per-bone weights on [skeleton], or null when the controller drives every bone. */
    fun build(skeleton: Skeleton): BoneMask? = when {
        isFullBody -> null
        preset == MaskPreset.UPPER_BODY -> BoneMask.upperBody(skeleton)
        preset == MaskPreset.LOWER_BODY -> BoneMask.invert(BoneMask.upperBody(skeleton))
        else -> BoneMask.subtree(skeleton, rootBone)
    }
}
