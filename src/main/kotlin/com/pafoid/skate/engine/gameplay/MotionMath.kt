package com.pafoid.skate.engine.gameplay

import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min

/** Pure motion helpers shared by character behaviors. */
object MotionMath {

    /** Moves [current] toward [target] by at most [maxDelta]. */
    fun approach(current: Float, target: Float, maxDelta: Float): Float = when {
        current < target -> min(current + maxDelta, target)
        else -> maxOf(current - maxDelta, target)
    }

    /** Horizontal world direction for a stick [input] (x = right, y = forward) given camera basis; zero when degenerate. */
    fun cameraRelativeDirection(forward: Vector3f, right: Vector3f, input: Vector2f, dest: Vector3f = Vector3f()): Vector3f {
        dest.set(forward).mul(input.y).fma(input.x, right)
        dest.y = 0f
        val len = dest.length()
        if (len.isNaN() || len < 1e-5f) return dest.zero()
        return dest.div(len)
    }

    /** Heading (rotation around Y, 0 = +Z) of a horizontal direction. */
    fun yawOfDirection(direction: Vector3f): Float = atan2(direction.x, direction.z)

    fun directionOfYaw(yaw: Float, dest: Vector3f = Vector3f()): Vector3f =
        dest.set(kotlin.math.sin(yaw), 0f, kotlin.math.cos(yaw))

    /** Rotation around Y of a quaternion, 0 = +Z. */
    fun yawOfRotation(q: Quaternionf): Float =
        atan2(2f * (q.w * q.y + q.x * q.z), 1f - 2f * (q.y * q.y + q.z * q.z))

    /** Wraps an angle in radians to (-PI, PI]. */
    fun wrapAngle(angle: Float): Float {
        var a = angle
        val twoPi = (2 * PI).toFloat()
        while (a > PI) a -= twoPi
        while (a <= -PI) a += twoPi
        return a
    }

    /** Exponential smoothing of an angle toward [target] along the shortest arc. */
    fun smoothAngle(current: Float, target: Float, rate: Float, dt: Float): Float {
        val t = (rate * dt).coerceIn(0f, 1f)
        return current + wrapAngle(target - current) * t
    }

    /** Linear interpolation over a curve defined by (minX -> minY) and (maxX -> maxY), clamped. */
    fun lerpClamped(x: Float, minX: Float, maxX: Float, minY: Float, maxY: Float): Float {
        if (maxX <= minX) return maxY
        val t = ((x - minX) / (maxX - minX)).coerceIn(0f, 1f)
        return minY + (maxY - minY) * t
    }
}
