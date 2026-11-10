package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.edit.EditTestData.frames
import com.pafoid.skate.engine.animation.edit.EditTestData.pitch
import com.pafoid.skate.engine.animation.edit.EditTestData.rotTrack
import com.pafoid.skate.engine.animation.ik.IkSolvers
import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FootLockTest {
    private val skeleton = TestHumanoid.skeleton()
    private val rig = ClipRig(skeleton)
    private val leg = LegChain("mixamorig:LeftUpLeg", "mixamorig:LeftLeg", "mixamorig:LeftFoot")
    private val times = frames(30f, 1f)

    /** Hips glide forward and dip; the left leg keeps a slight static bend, so the foot slides without a lock. */
    private val clip = SkClip(
        name = "slide", fps = 30f, duration = 1f,
        tracks = listOf(
            SkTrack("mixamorig:Hips", AnimationPath.TRANSLATION, InterpolationType.LINEAR, times.map { SkKey(it, listOf(0f, -0.08f * it, 0.25f * it)) }),
            rotTrack("mixamorig:LeftUpLeg", *times.map { it to pitch(-10f) }.toTypedArray()),
            rotTrack("mixamorig:LeftLeg", *times.map { it to pitch(20f) }.toTypedArray()),
        ),
    )

    private fun footAt(c: SkClip, t: Float): Vector3f =
        IkSolvers.globalOf(rig.parents, ClipEvaluator(c, rig).locals(t), rig.indexOf(leg.foot)).getTranslation(Vector3f())

    @Test
    fun `FootLock_FootStaysPlantedWhileTheBodyMoves`() {
        val start = footAt(clip, 0.2f)
        assertTrue(footAt(clip, 0.7f).distance(start) > 0.1f, "precondition: the foot slides without a lock")

        val locked = FootLock.apply(clip, skeleton, leg, 0.2f, 0.7f)
        for (t in listOf(0.2f, 0.3f, 0.45f, 0.5f, 0.6f, 0.7f)) {
            val d = footAt(locked, t).distance(start)
            assertTrue(d < 0.002f, "foot moved $d m at t=$t")
        }
        assertTrue(footAt(locked, 0.9f).distance(footAt(clip, 0.9f)) < 1e-4f, "outside the range the clip is unchanged")
        SkAnimFormat.validate(locked)
    }

    @Test
    fun `FootLock_BlendEasesInWithoutAPop`() {
        // On release the foot must catch up with the sliding body; a blend spreads that over several frames.
        fun worstStep(c: SkClip) = (0..30).map { footAt(c, it / 30f) }.zipWithNext { a, b -> a.distance(b) }.max()
        val hard = FootLock.apply(clip, skeleton, leg, 0.4f, 0.6f)
        val eased = FootLock.apply(clip, skeleton, leg, 0.4f, 0.6f, blendSeconds = 0.2f)
        assertTrue(worstStep(eased) < worstStep(hard) * 0.6f, "eased ${worstStep(eased)} vs hard ${worstStep(hard)}")
        assertTrue(footAt(eased, 0.5f).distance(footAt(eased, 0.4f)) < 0.002f, "still planted inside the range")
    }
}
