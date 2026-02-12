package com.pafoid.skate.engine.utils

import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * A utility object providing static methods for various interpolation techniques
 * commonly used in animation systems.
 * 
 * Supports:
 * - Linear Interpolation (LERP)
 * - Spherical Linear Interpolation (SLERP) for Quaternions
 * - Cubic Spline Interpolation (Hermite Spline)
 * - Step Interpolation
 */
object Interpolator {

    /**
     * Performs linear interpolation between two [Vector3f] points.
     * Uses JOML's internal implementation.
     * Formula: result = start + (end - start) * t
     *
     * @param start The starting vector.
     * @param end The ending vector.
     * @param t The interpolation factor, typically in the range [0, 1].
     * @param dest The destination vector to store the result.
     * @return The interpolated vector (same as dest).
     */
    fun linear(start: Vector3f, end: Vector3f, t: Float, dest: Vector3f): Vector3f {
        return start.lerp(end, t, dest)
    }

    /**
     * Performs spherical linear interpolation (SLERP) between two [Quaternionf]s.
     * This ensures the shortest path on the unit sphere and constant angular velocity.
     * Vital for smooth character rotations.
     *
     * @param start The starting quaternion.
     * @param end The ending quaternion.
     * @param t The interpolation factor, typically in the range [0, 1].
     * @param dest The destination quaternion to store the result.
     * @return The interpolated quaternion (same as dest).
     */
    fun slerp(start: Quaternionf, end: Quaternionf, t: Float, dest: Quaternionf): Quaternionf {
        return start.slerp(end, t, dest)
    }

    /**
     * Performs linear interpolation between two float values.
     * Formula: result = start + (end - start) * t
     *
     * @param start The starting value.
     * @param end The ending value.
     * @param t The interpolation factor, typically in the range [0, 1].
     * @return The interpolated value.
     */
    fun lerp(start: Float, end: Float, t: Float): Float {
        return start + (end - start) * t
    }

    /**
     * Performs cubic spline interpolation between two float values using Hermite interpolation.
     * This implementation follows the glTF 2.0 specification for CUBIC_SPLINE interpolation.
     *
     * Formula:
     * p(t) = (2t³ - 3t² + 1)p₀ + (t³ - 2t² + t)m₀ + (-2t³ + 3t²)p₁ + (t³ - t²)m₁
     *
     * Where:
     * - p₀: Starting point value
     * - p₁: Ending point value
     * - m₀: Out-tangent at start * deltaTime
     * - m₁: In-tangent at end * deltaTime
     * - t: Normalized time factor [0, 1]
     *
     * @param p0 The starting point value.
     * @param m0 The out-tangent (slope) at the starting point.
     * @param p1 The ending point value.
     * @param m1 The in-tangent (slope) at the ending point.
     * @param t The interpolation factor, typically in the range [0, 1].
     * @param deltaTime The time difference between keyframes, used to scale the tangents (m0, m1).
     * @return The smoothly interpolated value.
     */
    fun cubicSpline(
        p0: Float, m0: Float,
        p1: Float, m1: Float,
        t: Float,
        deltaTime: Float
    ): Float {
        val t2 = t * t
        val t3 = t2 * t
        
        val h00 = 2 * t3 - 3 * t2 + 1
        val h10 = t3 - 2 * t2 + t
        val h01 = -2 * t3 + 3 * t2
        val h11 = t3 - t2

        return h00 * p0 + h10 * deltaTime * m0 + h01 * p1 + h11 * deltaTime * m1
    }

    /**
     * Implements STEP interpolation for a [Vector3f]. The value remains constant at the starting value
     * for the entire interval, as defined by the glTF 2.0 specification.
     *
     * @param start The value to hold for the duration of the step.
     * @param t The interpolation factor (unused in this implementation, but kept for signature consistency).
     * @param dest The destination vector to store the result.
     * @return The starting vector.
     */
    fun step(start: Vector3f, t: Float, dest: Vector3f): Vector3f {
        return dest.set(start)
    }

    /**
     * Implements STEP interpolation for a [Quaternionf]. The value remains constant at the starting value
     * for the entire interval.
     *
     * @param start The quaternion to hold.
     * @param t The interpolation factor (unused).
     * @param dest The destination quaternion to store the result.
     * @return The starting quaternion.
     */
    fun step(start: Quaternionf, t: Float, dest: Quaternionf): Quaternionf {
        return dest.set(start)
    }

    /**
     * Performs cubic spline interpolation for a [Vector3f].
     * Formula: p(t) = (2t³ - 3t² + 1)p₀ + (t³ - 2t² + t)m₀ + (-2t³ + 3t²)p₁ + (t³ - t²)m₁
     * where p₀, p₁ are values, m₀ is out-tangent of start, m₁ is in-tangent of end.
     *
     * @param p0 The starting point vector.
     * @param m0 The out-tangent vector at the starting point.
     * @param p1 The ending point vector.
     * @param m1 The in-tangent vector at the ending point.
     * @param t The interpolation factor.
     * @param deltaTime The time difference between keyframes.
     * @param dest The destination vector to store the result.
     * @return The interpolated vector.
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

        dest.x = h00 * p0.x + h10 * deltaTime * m0.x + h01 * p1.x + h11 * deltaTime * m1.x
        dest.y = h00 * p0.y + h10 * deltaTime * m0.y + h01 * p1.y + h11 * deltaTime * m1.y
        dest.z = h00 * p0.z + h10 * deltaTime * m0.z + h01 * p1.z + h11 * deltaTime * m1.z
        
        return dest
    }

    /**
     * Performs cubic spline interpolation for a [Quaternionf]. The result is normalized.
     *
     * @param p0 The starting point quaternion.
     * @param m0 The out-tangent quaternion at the starting point.
     * @param p1 The ending point quaternion.
     * @param m1 The in-tangent quaternion at the ending point.
     * @param t The interpolation factor.
     * @param deltaTime The time difference between keyframes.
     * @param dest The destination quaternion to store the result.
     * @return The interpolated and normalized quaternion.
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
