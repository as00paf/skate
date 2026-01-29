package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.utils.Interpolation
import org.joml.Quaternionf
import org.joml.Vector3f

class AnimationSampler(
    val times: FloatArray,
    val values: FloatArray,
    val interpolation: InterpolationType,
    val componentsPerValue: Int
) {
    private fun findKeyframeIndex(time: Float): Int {
        var index = times.binarySearch(time)
        if (index < 0) {
            index = -(index + 2)
        }
        return index.coerceIn(0, times.size - 2)
    }

    fun sample(time: Float, dest: FloatArray) {
        if (times.isEmpty()) return
        val clampedTime = time.coerceIn(times.first(), times.last())
        
        if (clampedTime <= times.first()) {
            val stride = if (interpolation == InterpolationType.CUBIC_SPLINE) 3 * componentsPerValue else componentsPerValue
            val offset = if (interpolation == InterpolationType.CUBIC_SPLINE) componentsPerValue else 0
            for (i in 0 until componentsPerValue) {
                dest[i] = values[offset + i]
            }
            return
        }
        if (clampedTime >= times.last()) {
            val stride = if (interpolation == InterpolationType.CUBIC_SPLINE) 3 * componentsPerValue else componentsPerValue
            val offset = (times.size - 1) * stride + (if (interpolation == InterpolationType.CUBIC_SPLINE) componentsPerValue else 0)
            for (i in 0 until componentsPerValue) {
                dest[i] = values[offset + i]
            }
            return
        }

        val index = findKeyframeIndex(clampedTime)
        val t0 = times[index]
        val t1 = times[index + 1]
        val t = (clampedTime - t0) / (t1 - t0)

        when (interpolation) {
            InterpolationType.STEP -> {
                for (i in 0 until componentsPerValue) {
                    dest[i] = values[index * componentsPerValue + i]
                }
            }
            InterpolationType.LINEAR -> {
                for (i in 0 until componentsPerValue) {
                    val v0 = values[index * componentsPerValue + i]
                    val v1 = values[(index + 1) * componentsPerValue + i]
                    dest[i] = v0 + (v1 - v0) * t
                }
            }
            InterpolationType.CUBIC_SPLINE -> {
                val dt = t1 - t0
                val stride = 3 * componentsPerValue
                for (i in 0 until componentsPerValue) {
                    val p0 = values[index * stride + componentsPerValue + i]
                    val m0 = values[index * stride + componentsPerValue * 2 + i]
                    val p1 = values[(index + 1) * stride + componentsPerValue + i]
                    val m1 = values[(index + 1) * stride + i]
                    dest[i] = Interpolation.cubicSpline(p0, m0, p1, m1, t, dt)
                }
            }
        }
    }

    fun sampleVector3f(time: Float, dest: Vector3f) {
        if (times.isEmpty()) return
        if (time <= times.first()) {
            val stride = if (interpolation == InterpolationType.CUBIC_SPLINE) 9 else 3
            val offset = if (interpolation == InterpolationType.CUBIC_SPLINE) 3 else 0
            dest.set(values[offset], values[offset + 1], values[offset + 2])
            return
        }
        if (time >= times.last()) {
            val stride = if (interpolation == InterpolationType.CUBIC_SPLINE) 9 else 3
            val offset = (times.size - 1) * stride + (if (interpolation == InterpolationType.CUBIC_SPLINE) 3 else 0)
            dest.set(values[offset], values[offset + 1], values[offset + 2])
            return
        }

        val index = findKeyframeIndex(time)
        val t0 = times[index]
        val t1 = times[index + 1]
        val t = (time - t0) / (t1 - t0)

        when (interpolation) {
            InterpolationType.STEP -> {
                dest.set(values[index * 3], values[index * 3 + 1], values[index * 3 + 2])
            }
            InterpolationType.LINEAR -> {
                val v0x = values[index * 3]
                val v0y = values[index * 3 + 1]
                val v0z = values[index * 3 + 2]
                val v1x = values[(index + 1) * 3]
                val v1y = values[(index + 1) * 3 + 1]
                val v1z = values[(index + 1) * 3 + 2]
                dest.set(v0x + (v1x - v0x) * t, v0y + (v1y - v0y) * t, v0z + (v1z - v0z) * t)
            }
            InterpolationType.CUBIC_SPLINE -> {
                val dt = t1 - t0
                val stride = 9
                val p0x = values[index * stride + 3]
                val p0y = values[index * stride + 4]
                val p0z = values[index * stride + 5]
                val m0x = values[index * stride + 6]
                val m0y = values[index * stride + 7]
                val m0z = values[index * stride + 8]

                val p1x = values[(index + 1) * stride + 3]
                val p1y = values[(index + 1) * stride + 4]
                val p1z = values[(index + 1) * stride + 5]
                val m1x = values[(index + 1) * stride + 0]
                val m1y = values[(index + 1) * stride + 1]
                val m1z = values[(index + 1) * stride + 2]

                val t2 = t * t
                val t3 = t2 * t
                val h00 = 2 * t3 - 3 * t2 + 1
                val h10 = t3 - 2 * t2 + t
                val h01 = -2 * t3 + 3 * t2
                val h11 = t3 - t2

                dest.x = h00 * p0x + h10 * dt * m0x + h01 * p1x + h11 * dt * m1x
                dest.y = h00 * p0y + h10 * dt * m0y + h01 * p1y + h11 * dt * m1y
                dest.z = h00 * p0z + h10 * dt * m0z + h01 * p1z + h11 * dt * m1z
            }
        }
    }

    fun sampleQuaternionf(time: Float, dest: Quaternionf) {
        if (times.isEmpty()) return
        if (time <= times.first()) {
            val stride = if (interpolation == InterpolationType.CUBIC_SPLINE) 12 else 4
            val offset = if (interpolation == InterpolationType.CUBIC_SPLINE) 4 else 0
            dest.set(values[offset], values[offset + 1], values[offset + 2], values[offset + 3])
            return
        }
        if (time >= times.last()) {
            val stride = if (interpolation == InterpolationType.CUBIC_SPLINE) 12 else 4
            val offset = (times.size - 1) * stride + (if (interpolation == InterpolationType.CUBIC_SPLINE) 4 else 0)
            dest.set(values[offset], values[offset + 1], values[offset + 2], values[offset + 3])
            return
        }

        val index = findKeyframeIndex(time)
        val t0 = times[index]
        val t1 = times[index + 1]
        val t = (time - t0) / (t1 - t0)

        when (interpolation) {
            InterpolationType.STEP -> {
                dest.set(values[index * 4], values[index * 4 + 1], values[index * 4 + 2], values[index * 4 + 3])
            }
            InterpolationType.LINEAR -> {
                // glTF says LINEAR for rotations means SLERP
                val q0 = Quaternionf(values[index * 4], values[index * 4 + 1], values[index * 4 + 2], values[index * 4 + 3])
                val q1 = Quaternionf(values[(index + 1) * 4], values[(index + 1) * 4 + 1], values[(index + 1) * 4 + 2], values[(index + 1) * 4 + 3])
                q0.slerp(q1, t, dest)
            }
            InterpolationType.CUBIC_SPLINE -> {
                val dt = t1 - t0
                val stride = 12
                val p0x = values[index * stride + 4]; val p0y = values[index * stride + 5]; val p0z = values[index * stride + 6]; val p0w = values[index * stride + 7]
                val m0x = values[index * stride + 8]; val m0y = values[index * stride + 9]; val m0z = values[index * stride + 10]; val m0w = values[index * stride + 11]

                val p1x = values[(index + 1) * stride + 4]; val p1y = values[(index + 1) * stride + 5]; val p1z = values[(index + 1) * stride + 6]; val p1w = values[(index + 1) * stride + 7]
                val m1x = values[(index + 1) * stride + 0]; val m1y = values[(index + 1) * stride + 1]; val m1z = values[(index + 1) * stride + 2]; val m1w = values[(index + 1) * stride + 3]

                val t2 = t * t
                val t3 = t2 * t
                val h00 = 2 * t3 - 3 * t2 + 1
                val h10 = t3 - 2 * t2 + t
                val h01 = -2 * t3 + 3 * t2
                val h11 = t3 - t2

                dest.x = h00 * p0x + h10 * dt * m0x + h01 * p1x + h11 * dt * m1x
                dest.y = h00 * p0y + h10 * dt * m0y + h01 * p1y + h11 * dt * m1y
                dest.z = h00 * p0z + h10 * dt * m0z + h01 * p1z + h11 * dt * m1z
                dest.w = h00 * p0w + h10 * dt * m0w + h01 * p1w + h11 * dt * m1w
                dest.normalize()
            }
        }
    }
}
