package com.pafoid.skate.engine.utils

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkeletonMathTest {
    private fun translation(x: Float, y: Float, z: Float) = Matrix4f().translation(x, y, z)

    private fun twoBoneSkeleton(): Skeleton {
        val root = Bone(0, "root", translation(1f, 0f, 0f))
        val child = Bone(1, "child", translation(0f, 2f, 0f))
        root.addChild(child)
        return Skeleton(root, 2)
    }

    private fun position(m: Matrix4f) = m.getTranslation(Vector3f())

    @Test
    fun SkeletonMath_ComputeGlobalTransforms_ChildIsParentTimesLocal() {
        val skeleton = twoBoneSkeleton()
        val local = arrayOf(translation(1f, 0f, 0f), translation(0f, 2f, 0f))
        val global = Array(2) { Matrix4f() }

        SkeletonMath.computeGlobalTransforms(skeleton.rootBone, local, global)

        assertEquals(Vector3f(1f, 0f, 0f), position(global[0]))
        assertEquals(Vector3f(1f, 2f, 0f), position(global[1]))
    }

    @Test
    fun SkeletonMath_ComputeGlobalTransforms_UsesProvidedLocalsOverBoneLocal() {
        val skeleton = twoBoneSkeleton()
        val local = arrayOf(translation(10f, 0f, 0f), translation(0f, 0f, 5f))
        val global = Array(2) { Matrix4f() }

        SkeletonMath.computeGlobalTransforms(skeleton.rootBone, local, global)

        assertEquals(Vector3f(10f, 0f, 5f), position(global[1]))
        assertEquals(Vector3f(10f, 0f, 5f), position(skeleton.bones[1]!!.worldTransform))
    }

    @Test
    fun SkeletonMath_ComputeGlobalTransforms_OutOfRangeIndexFallsBackToBoneLocal() {
        val root = Bone(5, "root", translation(3f, 0f, 0f))
        val global = Array(1) { Matrix4f() }

        SkeletonMath.computeGlobalTransforms(root, arrayOf(Matrix4f()), global)

        assertEquals(Vector3f(3f, 0f, 0f), position(root.worldTransform))
        assertEquals(Vector3f(), position(global[0]))
    }

    @Test
    fun SkeletonMath_BuildSkinMatrices_BindPoseWithInverseBindIsIdentity() {
        val skeleton = twoBoneSkeleton()
        skeleton.bones[0]!!.inverseBindMatrix.set(translation(-1f, 0f, 0f))
        skeleton.bones[1]!!.inverseBindMatrix.set(translation(-1f, -2f, 0f))
        val pose = SkeletonPose(skeleton)
        val palette = Array(2) { Matrix4f().translation(9f, 9f, 9f) }

        SkeletonMath.buildSkinMatrices(pose, palette)

        palette.forEach { assertEquals(Vector3f(), position(it)) }
    }

    @Test
    fun SkeletonMath_BuildSkinMatricesFromGlobal_MissingBoneYieldsIdentity() {
        val skeleton = Skeleton(Bone(0, "root"), 2)
        val pose = SkeletonPose(skeleton)
        val palette = Array(2) { Matrix4f().translation(7f, 7f, 7f) }

        SkeletonMath.buildSkinMatricesFromGlobal(pose, palette)

        assertEquals(Matrix4f(), palette[1])
    }

    @Test
    fun SkeletonMath_ResetToBindPose_RestoresBindLocalTransforms() {
        val skeleton = twoBoneSkeleton()
        val pose = SkeletonPose(skeleton)
        pose.localTransforms[0].translation(50f, 50f, 50f)

        SkeletonMath.resetToBindPose(pose)

        assertEquals(Vector3f(1f, 0f, 0f), position(pose.localTransforms[0]))
        assertEquals(Vector3f(0f, 2f, 0f), position(pose.localTransforms[1]))
    }
}
