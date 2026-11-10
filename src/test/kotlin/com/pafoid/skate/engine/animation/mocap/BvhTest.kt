package com.pafoid.skate.engine.animation.mocap

import com.pafoid.skate.engine.animation.edit.ClipEvaluator
import com.pafoid.skate.engine.animation.edit.ClipRig
import com.pafoid.skate.engine.animation.edit.EditTestDataAccess
import com.pafoid.skate.engine.animation.edit.KeyEditing
import com.pafoid.skate.engine.animation.ik.IkSolvers
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BvhTest {
    private val motion = PoseSolver.solve(
        SyntheticPose.sequence(30f, listOf(SyntheticPose.landmarks(emptyMap()), SyntheticPose.landmarks(SyntheticPose.samplePose), SyntheticPose.landmarks(emptyMap()))),
        "pose", SolveOptions(smoothing = Smoothing.NONE),
    )

    @Test
    fun `Bvh_WriteThenReadKeepsSkeletonAndMotion`() {
        val text = Bvh.write(motion.skeleton, motion.clip)
        assertTrue(text.startsWith("HIERARCHY") && "MOTION" in text && "Frames: 3" in text)
        val back = Bvh.read(text, "pose", unitScale = 1f)
        assertEquals(motion.skeleton.boneCount, back.skeleton.boneCount)
        assertEquals(30f, back.clip.fps, 1e-3f)
        for (bone in motion.skeleton.getAllBones()) {
            val other = back.skeleton.getBoneByName(bone.name)!!
            assertTrue(bone.bindLocalTransform.getTranslation(Vector3f()).distance(other.bindLocalTransform.getTranslation(Vector3f())) < 1e-5f, bone.name)
            val a = motion.clip.track(bone.name, AnimationPath.ROTATION)!!
            val b = back.clip.track(bone.name, AnimationPath.ROTATION)!!
            for (t in listOf(0f, 1f / 30f, 2f / 30f)) {
                val angle = EditTestDataAccess.angle(KeyEditing.sample(a, t).toList(), KeyEditing.sample(b, t).toList())
                assertTrue(angle < 0.01f, "${bone.name} differs by $angle deg at t=$t")
            }
        }
        val hipsA = KeyEditing.sample(motion.clip.track("Hips", AnimationPath.TRANSLATION)!!, 1f / 30f)
        val hipsB = KeyEditing.sample(back.clip.track("Hips", AnimationPath.TRANSLATION)!!, 1f / 30f)
        for (i in 0 until 3) assertEquals(hipsA[i], hipsB[i], 1e-5f)
    }

    @Test
    fun `Bvh_ReadsCentimetresAndChannelOrder`() {
        // A two-joint arm in centimetres; the root turns 90 degrees about Z, which swings the child from +Y to -X.
        val text = """
            HIERARCHY
            ROOT Hips
            {
              OFFSET 0 100 0
              CHANNELS 6 Xposition Yposition Zposition Zrotation Xrotation Yrotation
              JOINT Spine
              {
                OFFSET 0 50 0
                CHANNELS 3 Zrotation Xrotation Yrotation
                End Site
                {
                  OFFSET 0 10 0
                }
              }
            }
            MOTION
            Frames: 2
            Frame Time: 0.0333333
            0 100 0 0 0 0 0 0 0
            10 100 0 90 0 0 0 0 0
        """.trimIndent()
        val bvh = Bvh.read(text, "arm")
        val rig = ClipRig(bvh.skeleton)
        assertEquals(1f, bvh.skeleton.rootBone.bindLocalTransform.getTranslation(Vector3f()).y, 1e-5f, "cm -> m")
        val locals = ClipEvaluator(bvh.clip, rig).locals(1f / 30f)
        val spine = IkSolvers.globalOf(rig.parents, locals, rig.indexOf("Spine")).getTranslation(Vector3f())
        assertEquals(0.1f - 0.5f, spine.x, 1e-3f, "root moved 10 cm on X, child swung to -X")
        assertEquals(1f, spine.y, 1e-3f)
    }

    @Test
    fun `Bvh_RejectsBrokenFiles`() {
        assertThrows<IllegalArgumentException> { Bvh.read("HIERARCHY ROOT Hips { OFFSET 0 0 0", "x") }
        assertThrows<IllegalArgumentException> { Bvh.read("HIERARCHY ROOT Hips { OFFSET 0 0 0 CHANNELS 1 Wrotation } MOTION Frames: 1 Frame Time: 0.1 5", "x") }
    }
}
