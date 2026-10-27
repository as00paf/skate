package com.pafoid.skate.engine.animation.ik

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IkTest {
    // Bones: 0 hip at (0,1,0), 1 knee 0.5 below, 2 ankle 0.5 below; the knee sits slightly forward (+Z).
    private fun leg(): Pair<Skeleton, Array<Matrix4f>> {
        val hip = Bone(0, "hip", Matrix4f().translation(0f, 1f, 0f))
        val knee = Bone(1, "knee", Matrix4f().translation(0f, -0.5f, 0.05f))
        val ankle = Bone(2, "ankle", Matrix4f().translation(0f, -0.5f, -0.05f))
        hip.addChild(knee); knee.addChild(ankle)
        val skeleton = Skeleton(hip, 3)
        return skeleton to arrayOf(Matrix4f(hip.localTransform), Matrix4f(knee.localTransform), Matrix4f(ankle.localTransform))
    }

    private val parents = intArrayOf(-1, 0, 1)

    private fun pos(locals: Array<Matrix4f>, bone: Int) =
        IkSolvers.globalOf(parents, locals, bone).getTranslation(Vector3f())

    @Test
    fun `TwoBone_EndReachesReachableTarget_AndKeepsBoneLengths`() {
        val (_, locals) = leg()
        val l1 = pos(locals, 0).distance(pos(locals, 1))
        val l2 = pos(locals, 1).distance(pos(locals, 2))
        val target = Vector3f(0.3f, 0.35f, 0.2f)

        assertTrue(IkSolvers.solveTwoBone(parents, locals, 0, 1, 2, target))

        assertEquals(0f, pos(locals, 2).distance(target), 1e-3f)
        assertEquals(l1, pos(locals, 0).distance(pos(locals, 1)), 1e-4f)
        assertEquals(l2, pos(locals, 1).distance(pos(locals, 2)), 1e-4f)
    }

    @Test
    fun `TwoBone_UnreachableTargetStretchesTowardsIt`() {
        val (_, locals) = leg()
        IkSolvers.solveTwoBone(parents, locals, 0, 1, 2, Vector3f(0f, -5f, 0f))
        val end = pos(locals, 2)
        assertEquals(0f, end.x, 1e-3f)
        assertTrue(end.y < 0.05f)
        assertEquals(1f, pos(locals, 0).distance(end), 1e-2f)
    }

    @Test
    fun `TwoBone_PoleSelectsBendSide`() {
        val target = Vector3f(0f, 0.4f, 0f)
        val (_, front) = leg()
        IkSolvers.solveTwoBone(parents, front, 0, 1, 2, target, pole = Vector3f(0f, 1f, 5f))
        val (_, back) = leg()
        IkSolvers.solveTwoBone(parents, back, 0, 1, 2, target, pole = Vector3f(0f, 1f, -5f))
        assertTrue(pos(front, 1).z > 0.1f)
        assertTrue(pos(back, 1).z < -0.1f)
        assertEquals(0f, pos(back, 2).distance(target), 1e-3f)
    }

    @Test
    fun `TwoBone_WeightBlendsBetweenAnimatedAndSolvedPose`() {
        val (_, base) = leg()
        val animatedEnd = pos(base, 2)
        val target = Vector3f(0.4f, 0.4f, 0f)

        val (_, half) = leg()
        IkSolvers.solveTwoBone(parents, half, 0, 1, 2, target, weight = 0.5f)
        val (_, zero) = leg()
        assertTrue(!IkSolvers.solveTwoBone(parents, zero, 0, 1, 2, target, weight = 0f))

        assertEquals(0f, pos(zero, 2).distance(animatedEnd), 1e-6f)
        val d = pos(half, 2).distance(target)
        assertTrue(d > 1e-2f && d < animatedEnd.distance(target))
    }

    @Test
    fun `TwoBone_EndRotationOrientsFootToTarget`() {
        val (_, locals) = leg()
        val wanted = Quaternionf().rotateX(Math.toRadians(30.0).toFloat())
        IkSolvers.solveTwoBone(parents, locals, 0, 1, 2, Vector3f(0.2f, 0.4f, 0.1f), endRotation = wanted)
        val actual = IkSolvers.globalOf(parents, locals, 2).getUnnormalizedRotation(Quaternionf()).normalize()
        assertEquals(1f, Math.abs(actual.dot(wanted)), 1e-3f)
    }

    @Test
    fun `LookAt_AimsAxisAtTarget_AndClampsAngle`() {
        val (_, locals) = leg()
        val axis = Vector3f(0f, 0f, 1f)
        val target = Vector3f(5f, 1f, 0f) // 90 degrees to the side of the hip's +Z
        IkSolvers.solveLookAt(parents, locals, 0, axis, target)
        var fwd = IkSolvers.globalOf(parents, locals, 0).transformDirection(Vector3f(axis)).normalize()
        assertEquals(1f, fwd.x, 1e-3f)

        val (_, limited) = leg()
        IkSolvers.solveLookAt(parents, limited, 0, axis, target, maxAngleRad = Math.toRadians(30.0).toFloat())
        fwd = IkSolvers.globalOf(parents, limited, 0).transformDirection(Vector3f(axis)).normalize()
        assertEquals(30f, Math.toDegrees(Math.acos(fwd.z.toDouble())).toFloat(), 0.1f)
    }

    @Test
    fun `IkPass_WorldTargetsFollowCharacterTransform_AndStaticPoseDoesNotDrift`() {
        val (skeleton, locals) = leg()
        val animatedEnd = pos(locals, 2)
        val rig = IkRig()
        // Character stands at x=10; foot pinned at world (10.3, 0.35, 0.2)
        val c = rig.addTwoBone(TwoBoneIkConstraint("hip", "knee", "ankle", Vector3f(10.3f, 0.35f, 0.2f), weight = 0.5f))
        val worldToModel = Matrix4f().translation(10f, 0f, 0f).invert()

        IkPass.run(rig, skeleton, locals, worldToModel, sourceFresh = true)
        val once = pos(locals, 2)
        repeat(5) { IkPass.run(rig, skeleton, locals, worldToModel, sourceFresh = false) }
        assertEquals(0f, pos(locals, 2).distance(once), 1e-4f)

        c.weight = 1f
        IkPass.run(rig, skeleton, locals, worldToModel, sourceFresh = false)
        assertEquals(0f, pos(locals, 2).distance(Vector3f(0.3f, 0.35f, 0.2f)), 1e-3f)

        c.weight = 0f
        IkPass.run(rig, skeleton, locals, worldToModel, sourceFresh = false)
        assertEquals(0f, pos(locals, 2).distance(animatedEnd), 1e-5f)
    }

    @Test
    fun `IkPass_IgnoresUnknownOrNonChainBones`() {
        val (skeleton, locals) = leg()
        val rig = IkRig()
        rig.addTwoBone(TwoBoneIkConstraint("hip", "ankle", "knee", Vector3f(), weight = 1f))
        rig.addTwoBone(TwoBoneIkConstraint("nope", "knee", "ankle", Vector3f(), weight = 1f))
        assertEquals(0, IkPass.apply(rig, skeleton, locals))
    }
}
