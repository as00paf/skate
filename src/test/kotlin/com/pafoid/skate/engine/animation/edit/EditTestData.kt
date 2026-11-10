package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import org.joml.Quaternionf
import org.junit.jupiter.api.Assertions.assertTrue

/** Small builders shared by the ANI-13 editing tests. */
internal object EditTestData {
    fun vecTrack(bone: String, vararg keys: Pair<Float, FloatArray>, interpolation: InterpolationType = InterpolationType.LINEAR, path: AnimationPath = AnimationPath.TRANSLATION) =
        SkTrack(bone, path, interpolation, keys.map { SkKey(it.first, it.second.toList()) })

    fun scalarTrack(times: FloatArray, values: (Float) -> Float, interpolation: InterpolationType = InterpolationType.LINEAR) =
        SkTrack("Bone", AnimationPath.TRANSLATION, interpolation, times.map { SkKey(it, listOf(values(it), 0f, 0f)) })

    fun rotTrack(bone: String, vararg keys: Pair<Float, Quaternionf>, interpolation: InterpolationType = InterpolationType.LINEAR) =
        SkTrack(bone, AnimationPath.ROTATION, interpolation, keys.map { SkKey(it.first, listOf(it.second.x, it.second.y, it.second.z, it.second.w)) })

    fun yaw(deg: Float) = Quaternionf().rotationY(Math.toRadians(deg.toDouble()).toFloat())
    fun pitch(deg: Float) = Quaternionf().rotationX(Math.toRadians(deg.toDouble()).toFloat())

    fun frames(fps: Float, duration: Float) = FloatArray((duration * fps).toInt() + 1) { it / fps }

    fun assertSameRotation(a: FloatArray, b: FloatArray, toleranceDeg: Float = 0.05f, message: String = "") {
        val angle = QuatMath.angleDeg(a, b)
        assertTrue(angle <= toleranceDeg, "$message rotation differs by $angle deg")
    }

    fun assertVec(expected: FloatArray, actual: FloatArray, eps: Float = 1e-4f, message: String = "") {
        for (i in expected.indices) assertTrue(kotlin.math.abs(expected[i] - actual[i]) <= eps, "$message component $i: expected ${expected[i]}, got ${actual[i]}")
    }
}
