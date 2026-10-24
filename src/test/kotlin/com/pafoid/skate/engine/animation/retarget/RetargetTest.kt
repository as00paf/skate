package com.pafoid.skate.engine.animation.retarget

import com.pafoid.skate.engine.animation.graph.BoneMask
import com.pafoid.skate.engine.animation.graph.Pose
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.AnimationChannel
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.AnimationSampler
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RetargetTest {

    private fun rig(vararg names: String): Skeleton {
        val bones = names.mapIndexed { i, n -> Bone(i, n, Matrix4f()) }
        for (i in 1 until bones.size) bones[i - 1].addChild(bones[i])
        return Skeleton(bones[0], bones.size)
    }

    @Test
    fun `HumanoidBone_RecognisesMixamoUnrealAndBipedNames`() {
        assertEquals(HumanoidBone.HIPS, HumanoidBone.fromName("mixamorig:Hips"))
        assertEquals(HumanoidBone.HIPS, HumanoidBone.fromName("pelvis"))
        assertEquals(HumanoidBone.LEFT_UPPER_LEG, HumanoidBone.fromName("mixamorig:LeftUpLeg"))
        assertEquals(HumanoidBone.LEFT_UPPER_LEG, HumanoidBone.fromName("thigh_l"))
        assertEquals(HumanoidBone.RIGHT_LOWER_ARM, HumanoidBone.fromName("RightForeArm"))
        assertEquals(HumanoidBone.RIGHT_LOWER_ARM, HumanoidBone.fromName("lowerarm_r"))
        assertEquals(HumanoidBone.LEFT_FOOT, HumanoidBone.fromName("Bip01 L Foot"))
        assertEquals(HumanoidBone.CHEST, HumanoidBone.fromName("Spine1"))
        assertEquals(HumanoidBone.SPINE, HumanoidBone.fromName("spine_01"))
    }

    @Test
    fun `HumanoidBone_LeftAndRightStayDistinct_AndUnknownIsNull`() {
        assertNotEquals(HumanoidBone.fromName("LeftHand"), HumanoidBone.fromName("RightHand"))
        assertNull(HumanoidBone.fromName("TailBone"))
        assertNull(HumanoidBone.fromName("Leg")) // sideless limb bone is ambiguous
    }

    @Test
    fun `Retargeter_MapsClipBetweenDifferentlyNamedRigs`() {
        val source = listOf("Hips", "LeftUpLeg", "LeftLeg", "RightArm")
        val target = rig("pelvis", "thigh_l", "calf_l", "upperarm_r")
        val map = Retargeter.buildMap(source, target)
        assertEquals(mapOf("Hips" to "pelvis", "LeftUpLeg" to "thigh_l", "LeftLeg" to "calf_l", "RightArm" to "upperarm_r"), map)
    }

    @Test
    fun `Retargeter_RenamesChannels_DropsUnmatched_ScalesTranslation`() {
        fun ch(name: String, path: AnimationPath, comps: Int) = AnimationChannel(
            AnimationSampler(floatArrayOf(0f, 1f), FloatArray(2 * comps) { 2f }, InterpolationType.LINEAR, comps), name, path
        )
        val anim = Animation(
            "walk",
            listOf(ch("Hips", AnimationPath.TRANSLATION, 3), ch("Hips", AnimationPath.ROTATION, 4), ch("Tail", AnimationPath.ROTATION, 4)),
            1f, "walk.fbx"
        )
        val target = rig("pelvis", "spine_01")
        val out = Retargeter.retarget(anim, target, translationScale = 0.5f)
        assertEquals(2, out.channels.size)
        assertEquals(setOf("pelvis"), out.channels.map { it.targetNodeName }.toSet())
        assertEquals(1f, out.channels.first { it.path == AnimationPath.TRANSLATION }.sampler.values[0], 1e-5f)
        assertEquals(2f, out.channels.first { it.path == AnimationPath.ROTATION }.sampler.values[0], 1e-5f)
        assertEquals(2f / 3f, Retargeter.coverage(anim, target), 1e-5f)
    }

    @Test
    fun `Retargeter_SameClipDrivesTwoRigs`() {
        val sampler = AnimationSampler(floatArrayOf(0f, 1f), floatArrayOf(0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f), InterpolationType.LINEAR, 4)
        val anim = Animation("a", listOf(AnimationChannel(sampler, "Hips", AnimationPath.ROTATION)), 1f, "a.fbx")
        for (names in listOf(arrayOf("Hips", "Spine"), arrayOf("pelvis", "spine_01"))) {
            val skel = rig(*names)
            val retargeted = Retargeter.retarget(anim, skel)
            retargeted.update(0.5f, skel)
            assertNotEquals(Matrix4f(), skel.getAllBones()[0].localTransform)
        }
    }

    @Test
    fun `BoneMask_UpperBodyCoversSpineSubtreeOnly`() {
        val skel = rig("Hips", "Spine", "Chest", "Head")
        val mask = BoneMask.upperBody(skel)
        assertEquals(listOf(0f, 1f, 1f, 1f), mask.weights.toList())
        assertEquals(listOf(1f, 0f, 0f, 0f), BoneMask.invert(mask).weights.toList())
    }

    @Test
    fun `PoseBlendMasked_OnlyAffectsMaskedBones`() {
        val base = Pose(2)
        val overlay = Pose(2)
        overlay.translations[0] = 10f; overlay.translations[3] = 10f
        val out = Pose(2)
        Pose.blendMasked(base, overlay, 1f, BoneMask(floatArrayOf(0f, 0.5f)), out)
        assertEquals(0f, out.translations[0], 1e-5f)
        assertEquals(5f, out.translations[3], 1e-5f)
    }
}
