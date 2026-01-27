package com.pafoid.skate.engine.utils

import org.joml.Quaternionf
import org.joml.Vector3f

object Interpolation {

    /**
     * LINEAR interpolation for Vector3f
     */
    fun linear(start: Vector3f, end: Vector3f, t: Float, dest: Vector3f): Vector3f {
        return start.lerp(end, t, dest)
    }

    /**
     * SLERP interpolation for Quaternionf (Linear in glTF for rotations)
     */
    fun slerp(start: Quaternionf, end: Quaternionf, t: Float, dest: Quaternionf): Quaternionf {
        return start.slerp(end, t, dest)
    }

    /**
     * STEP interpolation (returns start if t < 1, else end - but glTF STEP usually returns start for the whole interval)
     * In glTF: The animated value is set to the value of the keyframe at the beginning of the interval.
     */
    fun step(start: Vector3f, t: Float, dest: Vector3f): Vector3f {
        return dest.set(start)
    }

    fun step(start: Quaternionf, t: Float, dest: Quaternionf): Quaternionf {
        return dest.set(start)
    }

    /**
     * CUBICSPLINE interpolation for Vector3f
     * Formula: p(t) = (2t³ - 3t² + 1)p₀ + (t³ - 2t² + t)m₀ + (-2t³ + 3t²)p₁ + (t³ - t²)m₁
     * where p₀, p₁ are values, m₀ is out-tangent of start, m₁ is in-tangent of end.
     */
    fun cubicSpline(
        p0: Vector3f, m0: Vector3f,
        p1: Vector3f, m1: Vector3f,
        t: Float,
        deltaTime: Float,
        dest: Vector3f
    ): Vector3f {
        val t2 = t * t
        val t3 = t2 * t
        
        val h00 = 2 * t3 - 3 * t2 + 1
        val h10 = t3 - 2 * t2 + t
        val h01 = -2 * t3 + 3 * t2
        val h11 = t3 - t2

        // Note: tangents in glTF are already multiplied by the duration of the interval for some exporters,
        // but the spec says: "The units of the tangent are [units of property] / [units of time]".
        // However, for Cubic Spline: "the length of the interval is used to scale the tangents"
        // Actual formula: result = h00*p0 + h10*dt*m0 + h01*p1 + h11*dt*m1
        
        dest.set(0f, 0f, 0f)
        dest.add(Vector3f(p0).mul(h00))
        dest.add(Vector3f(m0).mul(h10 * deltaTime))
        dest.add(Vector3f(p1).mul(h01))
        dest.add(Vector3f(m1).mul(h11 * deltaTime))
        
        return dest
    }

    /**
     * CUBICSPLINE interpolation for Quaternionf
     */
    fun cubicSpline(
        p0: Quaternionf, m0: Quaternionf,
        p1: Quaternionf, m1: Quaternionf,
        t: Float,
        deltaTime: Float,
        dest: Quaternionf
    ): Quaternionf {
        val t2 = t * t
        val t3 = t2 * t
        
        val h00 = 2 * t3 - 3 * t2 + 1
        val h10 = t3 - 2 * t2 + t
        val h01 = -2 * t3 + 3 * t2
        val h11 = t3 - t2

        // Cubic spline for quaternions is often done via simple NLERP of the cubic result,
        // or just h00*p0 + h10*dt*m0 + h01*p1 + h11*dt*m1 followed by normalize.
        
        dest.set(
            h00 * p0.x + h10 * deltaTime * m0.x + h01 * p1.x + h11 * deltaTime * m1.x,
            h00 * p0.y + h10 * deltaTime * m0.y + h01 * p1.y + h11 * deltaTime * m1.y,
            h00 * p0.z + h10 * deltaTime * m0.z + h01 * p1.z + h11 * deltaTime * m1.z,
            h00 * p0.w + h10 * deltaTime * m0.w + h01 * p1.w + h11 * deltaTime * m1.w
        )
        dest.normalize()
        
        return dest
    }
}
