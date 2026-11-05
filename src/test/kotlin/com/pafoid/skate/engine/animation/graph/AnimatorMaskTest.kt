package com.pafoid.skate.engine.animation.graph

import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.ecs.components.Animator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class AnimatorMaskTest {
    private val skeleton = TestHumanoid.skeleton()
    private fun index(name: String) = skeleton.getBoneByName("mixamorig:$name")!!.index

    @Test
    fun `AnimatorMask_PresetsBuildTheExpectedWeights`() {
        assertNull(AnimatorMaskDef().build(skeleton))
        assertNull(AnimatorMaskDef(MaskPreset.SUBTREE, "").build(skeleton))

        val upper = AnimatorMaskDef(MaskPreset.UPPER_BODY).build(skeleton)!!
        assertEquals(1f, upper.weight(index("Head")))
        assertEquals(0f, upper.weight(index("LeftFoot")))

        val lower = AnimatorMaskDef(MaskPreset.LOWER_BODY).build(skeleton)!!
        assertEquals(0f, lower.weight(index("Head")))
        assertEquals(1f, lower.weight(index("LeftFoot")))

        val arm = AnimatorMaskDef(MaskPreset.SUBTREE, "LeftArm").build(skeleton)!!
        assertEquals(1f, arm.weight(index("LeftHand")))
        assertEquals(0f, arm.weight(index("RightHand")))
    }

    @Test
    fun `Animator_MaskIsCachedUntilChanged`() {
        val animator = Animator()
        val first = animator.boneMask(skeleton)
        assertNull(first)
        animator.mask = AnimatorMaskDef(MaskPreset.UPPER_BODY)
        val upper = animator.boneMask(skeleton)!!
        assertSame(upper, animator.boneMask(skeleton))
        animator.mask = AnimatorMaskDef(MaskPreset.LOWER_BODY)
        assertNotSame(upper, animator.boneMask(skeleton))
    }

    @Test
    fun `Animator_BasePoseIsBindPoseUnlessAClipPlays`() {
        val animator = Animator(animations = mutableListOf(Animation("idle", emptyList(), 1f, "idle.fbx")))
        val pose = Pose(skeleton.boneCount)
        animator.evaluateBasePose(skeleton, 0.1f, pose)
        val hips = index("Hips")
        assertEquals(1f, pose.translations[hips * 3 + 1], 1e-5f)
        assertEquals(0f, animator.currentTime)

        animator.currentAnimation = animator.animations[0]
        animator.isPlaying = true
        animator.evaluateBasePose(skeleton, 0.1f, pose)
        assertEquals(0.1f, animator.currentTime, 1e-6f)
    }
}
