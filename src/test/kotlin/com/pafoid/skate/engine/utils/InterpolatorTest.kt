package com.pafoid.skate.engine.utils

import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InterpolatorTest {

    @Test
    fun `test linear interpolation`() {
        val start = Vector3f(0f, 0f, 0f)
        val end = Vector3f(10f, 10f, 10f)
        val dest = Vector3f()

        Interpolator.linear(start, end, 0.5f, dest)
        assertEquals(5f, dest.x)
        assertEquals(5f, dest.y)
        assertEquals(5f, dest.z)
    }

    @Test
    fun `test step interpolation`() {
        val start = Vector3f(0f, 0f, 0f)
        val dest = Vector3f()

        Interpolator.step(start, 0.5f, dest)
        assertEquals(0f, dest.x)

        Interpolator.step(start, 0.99f, dest)
        assertEquals(0f, dest.x)
    }

    @Test
    fun `test slerp interpolation`() {
        val start = Quaternionf().rotationXYZ(0f, 0f, 0f)
        val end = Quaternionf().rotationXYZ(Math.toRadians(90.0).toFloat(), 0f, 0f)
        val dest = Quaternionf()

        Interpolator.slerp(start, end, 0.5f, dest)
        
        val expected = Quaternionf().rotationXYZ(Math.toRadians(45.0).toFloat(), 0f, 0f)
        assertEquals(expected.x, dest.x, 1e-6f)
        assertEquals(expected.y, dest.y, 1e-6f)
        assertEquals(expected.z, dest.z, 1e-6f)
        assertEquals(expected.w, dest.w, 1e-6f)
    }

    @Test
    fun `test cubic spline interpolation`() {
        val p0 = Vector3f(0f, 0f, 0f)
        val m0 = Vector3f(1f, 0f, 0f) // Tangent at p0
        val p1 = Vector3f(1f, 0f, 0f)
        val m1 = Vector3f(1f, 0f, 0f) // Tangent at p1
        val dest = Vector3f()
        
        // With t=0, should be p0
        Interpolator.cubicSpline(p0, m0, p1, m1, 0f, 1f, dest)
        assertEquals(0f, dest.x)
        
        // With t=1, should be p1
        Interpolator.cubicSpline(p0, m0, p1, m1, 1f, 1f, dest)
        assertEquals(1f, dest.x)
        
        // With t=0.5 and constant velocity (tangents match distance), should be 0.5
        Interpolator.cubicSpline(p0, m0, p1, m1, 0.5f, 1f, dest)
        assertEquals(0.5f, dest.x, 1e-6f)
    }
}
