package com.pafoid.skate.engine.animation.ik

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IkDebugDrawTest {
    private fun leg(): Pair<Skeleton, Array<Matrix4f>> {
        val hip = Bone(0, "hip", Matrix4f().translation(0f, 1f, 0f))
        val knee = Bone(1, "knee", Matrix4f().translation(0f, -0.5f, 0.05f))
        val ankle = Bone(2, "ankle", Matrix4f().translation(0f, -0.5f, -0.05f))
        hip.addChild(knee); knee.addChild(ankle)
        return Skeleton(hip, 3) to arrayOf(Matrix4f(hip.localTransform), Matrix4f(knee.localTransform), Matrix4f(ankle.localTransform))
    }

    private class Line(val from: Vector3f, val to: Vector3f, val color: Vector3f)

    private fun collect(rig: IkRig, modelToWorld: Matrix4f? = null): List<Line> {
        val (skeleton, locals) = leg()
        val out = mutableListOf<Line>()
        IkDebugDraw.collect(rig, skeleton, locals, modelToWorld) { f, t, c -> out += Line(Vector3f(f), Vector3f(t), Vector3f(c)) }
        return out
    }

    @Test
    fun `DebugDraw_InactiveRigEmitsNothing`() {
        val rig = IkRig()
        rig.addTwoBone(TwoBoneIkConstraint("hip", "knee", "ankle", weight = 0f))
        assertTrue(collect(rig).isEmpty())
    }

    @Test
    fun `DebugDraw_TwoBoneDrawsChainTargetCrossAndPole`() {
        val rig = IkRig()
        rig.addTwoBone(TwoBoneIkConstraint("hip", "knee", "ankle", Vector3f(1f, 0f, 0f), pole = Vector3f(0f, 1f, 2f), weight = 1f, space = IkSpace.WORLD))
        val lines = collect(rig, Matrix4f().translation(5f, 0f, 0f))
        assertEquals(2, lines.count { it.color == IkDebugDraw.CHAIN })
        assertEquals(3, lines.count { it.color == IkDebugDraw.TARGET })
        // Chain is drawn in world space (model offset by +5 on x); a WORLD target is not offset again.
        assertEquals(5f, lines.first { it.color == IkDebugDraw.CHAIN }.from.x, 1e-5f)
        val miss = lines.single { it.color == IkDebugDraw.MISS }
        assertEquals(1f, miss.to.x, 1e-5f)
        assertEquals(1, lines.count { it.color == IkDebugDraw.POLE && it.from.y == 0.5f })
    }

    @Test
    fun `DebugDraw_ModelSpaceTargetsAreMovedToWorld`() {
        val rig = IkRig()
        rig.addTwoBone(TwoBoneIkConstraint("hip", "knee", "ankle", Vector3f(1f, 0f, 0f), weight = 1f, space = IkSpace.MODEL))
        val miss = collect(rig, Matrix4f().translation(5f, 0f, 0f)).single { it.color == IkDebugDraw.MISS }
        assertEquals(6f, miss.to.x, 1e-5f)
    }

    @Test
    fun `DebugDraw_LookAtDrawsRayAlongAxisAndTargetLine`() {
        val rig = IkRig()
        rig.addLookAt(LookAtConstraint("hip", Vector3f(0f, 1f, 3f), weight = 1f, space = IkSpace.WORLD))
        val lines = collect(rig)
        val ray = lines.single { it.color == IkDebugDraw.LOOK_RAY }
        assertEquals(IkDebugDraw.LOOK_RAY_LENGTH, ray.from.distance(ray.to), 1e-5f)
        assertEquals(IkDebugDraw.LOOK_RAY_LENGTH, ray.to.z - ray.from.z, 1e-5f) // +Z axis, identity pose
        assertEquals(4, lines.count { it.color == IkDebugDraw.LOOK_TARGET })
    }
}
