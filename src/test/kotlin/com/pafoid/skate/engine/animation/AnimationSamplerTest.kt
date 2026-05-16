package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.assets.data.models.animations.AnimationSampler
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs

class AnimationSamplerTest {

    @Test
    fun `test linear vector3f interpolation`() {
        val times = floatArrayOf(0f, 1f)
        val values = floatArrayOf(
            0f, 0f, 0f, // t=0
            10f, 20f, 30f // t=1
        )
        val sampler = AnimationSampler(times, values, InterpolationType.LINEAR, 3)
        val result = Vector3f()

        sampler.sampleVector3f(0.5f, result)
        assertEquals(5f, result.x, 1e-5f)
        assertEquals(10f, result.y, 1e-5f)
        assertEquals(15f, result.z, 1e-5f)

        sampler.sampleVector3f(-1f, result)
        assertEquals(0f, result.x)

        sampler.sampleVector3f(2f, result)
        assertEquals(10f, result.x)
    }

    @Test
    fun `test step interpolation`() {
        val times = floatArrayOf(0f, 1f, 2f)
        val values = floatArrayOf(
            0f, 0f, 0f, // t=0
            10f, 10f, 10f, // t=1
            20f, 20f, 20f  // t=2
        )
        val sampler = AnimationSampler(times, values, InterpolationType.STEP, 3)
        val result = Vector3f()

        sampler.sampleVector3f(0.5f, result)
        assertEquals(0f, result.x)

        sampler.sampleVector3f(1.5f, result)
        assertEquals(10f, result.x)
        
        sampler.sampleVector3f(2f, result)
        assertEquals(20f, result.x)
    }

    @Test
    fun `test cubic spline vector3f interpolation`() {
        // For CUBICSPLINE, glTF stores: in-tangent, value, out-tangent per keyframe
        // stride = 3 * componentsPerValue = 9 for Vector3f
        val times = floatArrayOf(0f, 1f)
        val values = floatArrayOf(
            0f, 0f, 0f,  // in-tangent (not used for first keyframe)
            0f, 0f, 0f,  // value
            10f, 0f, 0f, // out-tangent
            
            -10f, 0f, 0f, // in-tangent
            10f, 0f, 0f,  // value
            0f, 0f, 0f    // out-tangent (not used for last keyframe)
        )
        val sampler = AnimationSampler(times, values, InterpolationType.CUBIC_SPLINE, 3)
        val result = Vector3f()

        // At t=0.5, h00=0.5, h10=0.125, h01=0.5, h11=-0.125
        // dt = 1
        // x = 0.5*0 + 0.125*1*10 + 0.5*10 + (-0.125)*1*(-10)
        // x = 0 + 1.25 + 5 + 1.25 = 7.5
        sampler.sampleVector3f(0.5f, result)
        assertEquals(7.5f, result.x, 1e-5f)
    }

    @Test
    fun `test generic sample method`() {
        val times = floatArrayOf(0f, 1f)
        val values = floatArrayOf(
            1f, 2f, 3f, 4f, 5f, // t=0
            10f, 20f, 30f, 40f, 50f // t=1
        )
        val sampler = AnimationSampler(times, values, InterpolationType.LINEAR, 5)
        val result = FloatArray(5)

        sampler.sample(0.5f, result)
        assertEquals(5.5f, result[0], 1e-5f)
        assertEquals(11f, result[1], 1e-5f)
        assertEquals(16.5f, result[2], 1e-5f)
        assertEquals(22f, result[3], 1e-5f)
        assertEquals(27.5f, result[4], 1e-5f)
    }

    @Test
    fun `test linear quaternion interpolation`() {
        val times = floatArrayOf(0f, 1f)
        val q0 = Quaternionf().rotateX(0f)
        val q1 = Quaternionf().rotateX(Math.toRadians(90.0).toFloat())
        val values = floatArrayOf(
            q0.x, q0.y, q0.z, q0.w,
            q1.x, q1.y, q1.z, q1.w
        )
        val sampler = AnimationSampler(times, values, InterpolationType.LINEAR, 4)
        val result = Quaternionf()

        sampler.sampleQuaternionf(0.5f, result)
        val expected = Quaternionf().rotateX(Math.toRadians(45.0).toFloat())
        
        // Check if dot product is close to 1 (same orientation)
        assertEquals(1.0f, abs(result.dot(expected)), 1e-5f)
    }
}
