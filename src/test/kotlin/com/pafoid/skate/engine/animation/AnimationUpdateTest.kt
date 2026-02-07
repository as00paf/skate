package com.pafoid.skate.engine.animation

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnimationUpdateTest {

    @Test
    fun `test animation update accumulates multiple channels on same bone`() {
        // 1. Setup Bone
        val boneIndex = 0
        val boneName = "TestBone"
        // Initial bind pose is identity
        val initialTransform = Matrix4f().identity()
        val bone = Bone(boneIndex, boneName, initialTransform)
        
        // 2. Setup Skeleton
        val skeleton = Skeleton(bone, 1)

        // 3. Setup Animation Channels
        // Channel 1: Translation to (10, 0, 0)
        val translationTimes = floatArrayOf(0f, 1f)
        val translationValues = floatArrayOf(
            10f, 0f, 0f, // t=0
            10f, 0f, 0f  // t=1
        )
        val translationSampler = AnimationSampler(translationTimes, translationValues, InterpolationType.STEP, 3)
        val translationChannel = AnimationChannel(translationSampler, boneName, AnimationPath.TRANSLATION)

        // Channel 2: Rotation 90 degrees around Y
        val rotationTimes = floatArrayOf(0f, 1f)
        val q0 = Quaternionf().rotateY(Math.toRadians(90.0).toFloat())
        val rotationValues = floatArrayOf(
            q0.x, q0.y, q0.z, q0.w, // t=0
            q0.x, q0.y, q0.z, q0.w  // t=1
        )
        val rotationSampler = AnimationSampler(rotationTimes, rotationValues, InterpolationType.STEP, 4)
        val rotationChannel = AnimationChannel(rotationSampler, boneName, AnimationPath.ROTATION)

        // Channel 3: Scale to (2, 2, 2)
        val scaleTimes = floatArrayOf(0f, 1f)
        val scaleValues = floatArrayOf(
            2f, 2f, 2f, // t=0
            2f, 2f, 2f  // t=1
        )
        val scaleSampler = AnimationSampler(scaleTimes, scaleValues, InterpolationType.STEP, 3)
        val scaleChannel = AnimationChannel(scaleSampler, boneName, AnimationPath.SCALE)

        // 4. Create Animation
        val animation = Animation("TestAnim", listOf(translationChannel, rotationChannel, scaleChannel), 1.0f)

        // 5. Update Animation at t=0
        animation.update(0f, skeleton)

        // 6. Verify Results
        val resultPos = Vector3f()
        val resultRot = Quaternionf()
        val resultScale = Vector3f()
        bone.localTransform.getTranslation(resultPos)
        bone.localTransform.getUnnormalizedRotation(resultRot)
        bone.localTransform.getScale(resultScale)

        // Verify Translation (should be 10, 0, 0)
        assertEquals(10f, resultPos.x, 1e-5f, "Translation X should be 10")
        assertEquals(0f, resultPos.y, 1e-5f, "Translation Y should be 0")
        assertEquals(0f, resultPos.z, 1e-5f, "Translation Z should be 0")

        // Verify Rotation (should be 90 deg around Y)
        val expectedRot = Quaternionf().rotateY(Math.toRadians(90.0).toFloat())
        assertEquals(expectedRot.x, resultRot.x, 1e-5f, "Rotation X mismatch")
        assertEquals(expectedRot.y, resultRot.y, 1e-5f, "Rotation Y mismatch")
        assertEquals(expectedRot.z, resultRot.z, 1e-5f, "Rotation Z mismatch")
        assertEquals(expectedRot.w, resultRot.w, 1e-5f, "Rotation W mismatch")

        // Verify Scale (should be 2, 2, 2)
        assertEquals(2f, resultScale.x, 1e-5f, "Scale X should be 2")
        assertEquals(2f, resultScale.y, 1e-5f, "Scale Y should be 2")
        assertEquals(2f, resultScale.z, 1e-5f, "Scale Z should be 2")
    }
}
