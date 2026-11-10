package com.pafoid.skate.engine.animation.mocap

import com.pafoid.skate.engine.animation.edit.ClipEvaluator
import com.pafoid.skate.engine.animation.edit.ClipRig
import com.pafoid.skate.engine.animation.ik.IkSolvers
import com.pafoid.skate.engine.animation.mocap.SyntheticPose.deg
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class PoseSolverTest {
    private val noSmoothing = SolveOptions(smoothing = Smoothing.NONE)

    private fun solvedPositions(motion: SolvedMotion, time: Float): Map<String, Vector3f> {
        val rig = ClipRig(motion.skeleton)
        val locals = ClipEvaluator(motion.clip, rig).locals(time)
        return rig.names.indices.associate { rig.names[it] to IkSolvers.globalOf(rig.parents, locals, it).getTranslation(Vector3f()) }
    }

    /** Joint positions relative to the hips, for comparing shapes independently of grounding. */
    private fun relative(p: Map<String, Vector3f>) = p.mapValues { Vector3f(it.value).sub(p.getValue(MocapSkeleton.HIPS)) }

    @Test
    fun `Solver_ReproducesASyntheticPose`() {
        val seq = SyntheticPose.sequence(30f, List(3) { SyntheticPose.landmarks(SyntheticPose.samplePose) })
        val motion = PoseSolver.solve(seq, "pose", noSmoothing)
        val expected = relative(SyntheticPose.positions(SyntheticPose.samplePose))
        val actual = relative(solvedPositions(motion, 0f))
        val joints = listOf("LeftForeArm", "LeftHand", "RightForeArm", "RightHand", "LeftLeg", "LeftFoot", "RightLeg", "RightFoot", "RightToeBase", "Head")
        for (j in joints) {
            val d = expected.getValue(j).distance(actual.getValue(j))
            assertTrue(d < 0.01f, "$j is $d m off")
        }
    }

    @Test
    fun `Solver_MeasuresProportionsFromTheLandmarks`() {
        val seq = SyntheticPose.sequence(30f, List(3) { SyntheticPose.landmarks(SyntheticPose.samplePose) })
        val p = PoseSolver.solve(seq, "pose", noSmoothing).proportions
        val e = SyntheticPose.proportions
        assertEquals(e.thigh, p.thigh, 1e-3f); assertEquals(e.shin, p.shin, 1e-3f)
        assertEquals(e.upperArm, p.upperArm, 1e-3f); assertEquals(e.spine, p.spine, 1e-3f)
        assertEquals(e.hipHalfWidth, p.hipHalfWidth, 1e-3f)
    }

    @Test
    fun `Solver_PutsTheLowestFootOnTheGround`() {
        // Crouch: both knees bent, hips lowered; the solved feet must stay on the ground.
        val crouch = mapOf(
            "LeftUpLeg" to Quaternionf().rotationX(deg(-50f)), "LeftLeg" to Quaternionf().rotationX(deg(90f)), "LeftFoot" to Quaternionf().rotationX(deg(-40f)),
            "RightUpLeg" to Quaternionf().rotationX(deg(-50f)), "RightLeg" to Quaternionf().rotationX(deg(90f)), "RightFoot" to Quaternionf().rotationX(deg(-40f)),
        )
        val seq = SyntheticPose.sequence(30f, listOf(SyntheticPose.landmarks(emptyMap()), SyntheticPose.landmarks(crouch)))
        val motion = PoseSolver.solve(seq, "crouch", noSmoothing)
        for (t in listOf(0f, 1f / 30f)) {
            val p = solvedPositions(motion, t)
            val lowest = listOf("LeftToeBase", "RightToeBase", "LeftFoot", "RightFoot").minOf { p.getValue(it).y }
            assertEquals(0f, lowest, 0.03f, "feet on the ground at t=$t")
        }
        assertTrue(solvedPositions(motion, 1f / 30f).getValue(MocapSkeleton.HIPS).y < solvedPositions(motion, 0f).getValue(MocapSkeleton.HIPS).y - 0.1f, "hips go down")
    }

    @Test
    fun `Solver_RootMotionFollowsTheStanceFoot`() {
        // The left foot is planted and slides back 2 cm per frame relative to the hips: the body walks forward 2 cm/frame.
        val frames = (0 until 10).map { f ->
            val pose = mapOf(
                "LeftUpLeg" to Quaternionf().rotationX(deg(-10f + 2f * f)),
                "RightUpLeg" to Quaternionf().rotationX(deg(-20f)),
                "RightLeg" to Quaternionf().rotationX(deg(40f)),
            )
            SyntheticPose.landmarks(pose)
        }
        val seq = SyntheticPose.sequence(30f, frames)
        val inPlace = PoseSolver.solve(seq, "walk", noSmoothing)
        assertEquals(0f, solvedPositions(inPlace, 9f / 30f).getValue(MocapSkeleton.HIPS).z, 1e-4f, "in place by default")

        val moving = PoseSolver.solve(seq, "walk", SolveOptions(smoothing = Smoothing.NONE, rootMotion = true, footLock = false))
        val start = solvedPositions(moving, 0f)
        val end = solvedPositions(moving, 9f / 30f)
        val footStart = start.getValue("LeftToeBase"); val footEnd = end.getValue("LeftToeBase")
        assertTrue(end.getValue(MocapSkeleton.HIPS).z > start.getValue(MocapSkeleton.HIPS).z + 0.05f, "the body moves forward")
        assertTrue(Vector3f(footEnd).sub(footStart).length() < 0.03f, "the stance foot stays put: ${footStart} -> ${footEnd}")
        assertTrue(moving.contacts.left.all { it }, "left foot is in contact")
    }

    @Test
    fun `Solver_FillsFramesWithoutADetection`() {
        val a = SyntheticPose.landmarks(emptyMap())
        val b = SyntheticPose.landmarks(SyntheticPose.samplePose)
        val seq = LandmarkSequence(30f, 0, 0, listOf(
            LandmarkFrame(0f, null, null, null), SyntheticPose.frame(1 / 30f, a), LandmarkFrame(2 / 30f, null, null, null), SyntheticPose.frame(3 / 30f, b),
        ))
        val filled = PoseSolver.fillGaps(seq)
        assertEquals(4, filled.size)
        assertEquals(a[PoseLandmark.LEFT_WRIST * 3], filled[0][PoseLandmark.LEFT_WRIST].x, 1e-6f, "leading gap copies the first detection")
        val midX = (a[PoseLandmark.LEFT_WRIST * 3] + b[PoseLandmark.LEFT_WRIST * 3]) / 2f
        assertEquals(midX, filled[2][PoseLandmark.LEFT_WRIST].x, 1e-5f, "inner gap is interpolated")

        assertThrows<IllegalArgumentException> { PoseSolver.solve(LandmarkSequence(30f, 0, 0, listOf(LandmarkFrame(0f, null, null, null))), "none") }
    }

    @Test
    fun `Solver_SmoothingRemovesJitter`() {
        val random = Random(5)
        val frames = List(60) {
            SyntheticPose.landmarks(SyntheticPose.samplePose).also { p -> for (i in p.indices) p[i] += (random.nextFloat() - 0.5f) * 0.03f }
        }
        val seq = SyntheticPose.sequence(30f, frames)
        fun jitter(options: SolveOptions): Double {
            val track = PoseSolver.solve(seq, "j", options).clip.track("RightForeArm", com.pafoid.skate.engine.assets.data.models.animations.AnimationPath.ROTATION)!!
            return track.keys.zipWithNext { a, b ->
                com.pafoid.skate.engine.animation.edit.EditTestDataAccess.angle(a.value, b.value).toDouble()
            }.drop(10).average()
        }
        val raw = jitter(noSmoothing)
        val smooth = jitter(SolveOptions(smoothing = Smoothing.HIGH))
        assertTrue(smooth < raw * 0.5, "frame-to-frame rotation change $raw -> $smooth deg")
    }

    @Test
    fun `Solver_ContactRanges`() {
        val r = PoseSolver.ranges(booleanArrayOf(true, true, false, true, true, true, false, true), minLength = 3)
        assertEquals(listOf(3 to 5), r)
        assertFalse(PoseSolver.ranges(BooleanArray(0), 1).any())
    }
}
