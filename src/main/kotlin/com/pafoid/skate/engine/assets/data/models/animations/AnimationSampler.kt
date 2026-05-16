package com.pafoid.skate.engine.assets.data.models.animations

import com.pafoid.skate.engine.utils.Interpolator
import kotlinx.serialization.Serializable
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Responsible for interpolating animation data (translation, rotation, scale) over time.
 * 
 * Supports standard glTF 2.0 interpolation methods:
 * - [InterpolationType.STEP]: Holds the current value until the next keyframe.
 * - [InterpolationType.LINEAR]: Linearly interpolates between values (SLERP for rotations).
 * - [InterpolationType.CUBIC_SPLINE]: Uses Hermite spline interpolation for smooth transitions.
 * 
 * @param times Array of keyframe timestamps in seconds.
 * @param values Array of raw component values (x, y, z, etc.).
 * @param interpolation The interpolation algorithm to use.
 * @param componentsPerValue Number of floats per keyframe (3 for Vec3, 4 for Quat).
 */
@Serializable
data class AnimationSampler(
    val times: FloatArray,
    val values: FloatArray,
    val interpolation: InterpolationType,
    val componentsPerValue: Int
) {
    /**
     * Finds the index of the last keyframe whose timestamp is less than or equal to [time].
     * Uses binary search for efficient lookup within the sorted [times] array.
     *
     * @param time The current animation time in seconds.
     * @return The index of the keyframe just before or at the given time. 
     *         Clamped to valid range [0, times.size - 2] to ensure a next keyframe exists.
     */
    private fun findKeyframeIndex(time: Float): Int {
        var index = times.binarySearch(time)
        if (index < 0) {
            // binarySearch returns (-(insertion point) - 1) if not found.
            // The insertion point is the index of the first element greater than the key.
            // We want the element *before* that, so:
            // index = -index - 1 (insertion point)
            // result = insertion point - 1
            // result = (-index - 1) - 1 = -index - 2
            index = -(index + 2)
        }
        return index.coerceIn(0, times.size - 2)
    }

    /**
     * Encapsulates the shared logic for sampling animation data.
     * It handles:
     * 1. Clamping the input time to the animation's duration.
     * 2. Handling boundary conditions (before start, after end).
     * 3. Calculating the interpolation factor (t) and delta time between keyframes.
     * 4. Calculating the correct indices and offsets into the [values] array based on stride.
     *
     * @param time The current animation time.
     * @param components The number of float components per value (e.g., 3 for Vector3, 4 for Quaternion).
     * @param block The lambda to execute for interpolation when within valid keyframes. 
     *              Receives (keyframeIndex, t, deltaTime).
     * @param boundaryBlock The lambda to execute when the time is out of bounds (clamped to start or end).
     *                      Receives the offset into the [values] array.
     */
    private inline fun withSampleContext(
        time: Float,
        components: Int,
        block: (index: Int, t: Float, dt: Float) -> Unit,
        boundaryBlock: (offset: Int) -> Unit
    ) {
        if (times.isEmpty()) return
        val clampedTime = time.coerceIn(times.first(), times.last())

        // Handle start boundary (clamp to first frame)
        if (clampedTime <= times.first()) {
            // For Cubic Spline, the first keyframe has 3 sets of values: in-tangent, value, out-tangent.
            // The value is the middle one.
            val offset = if (interpolation == InterpolationType.CUBIC_SPLINE) components else 0
            boundaryBlock(offset)
            return
        }
        
        // Handle end boundary (clamp to last frame)
        if (clampedTime >= times.last()) {
            // Calculate stride based on interpolation type
            val stride = if (interpolation == InterpolationType.CUBIC_SPLINE) 3 * components else components
            val offset = (times.size - 1) * stride + (if (interpolation == InterpolationType.CUBIC_SPLINE) components else 0)
            boundaryBlock(offset)
            return
        }

        val index = findKeyframeIndex(clampedTime)
        val previousKeyframeTime = times[index]
        val nextKeyframeTime = times[index + 1]
        
        // precise time factor 0..1 between the two keyframes
        val interpolationFactor = (clampedTime - previousKeyframeTime) / (nextKeyframeTime - previousKeyframeTime)
        
        block(index, interpolationFactor, nextKeyframeTime - previousKeyframeTime)
    }

    /**
     * Samples the animation at a specific [time] and writes the generic float array result into [dest].
     * Useful for morph targets or scalar animations.
     *
     * @param time The time in seconds to sample at.
     * @param dest The array to write the result into. Must be at least [componentsPerValue] in size.
     */
    fun sample(time: Float, dest: FloatArray) {
        withSampleContext(time, componentsPerValue, { index, interpolationFactor, deltaTime ->
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
                        dest[i] = v0 + (v1 - v0) * interpolationFactor
                    }
                }
                InterpolationType.CUBIC_SPLINE -> {
                    val stride = 3 * componentsPerValue
                    for (i in 0 until componentsPerValue) {
                        // Layout: [in-tangent, value, out-tangent]
                        // P0 (start value) is at index*stride + components (middle)
                        // M0 (start out-tangent) is at index*stride + components*2 (end)
                        // P1 (end value) is at (index+1)*stride + components (middle)
                        // M1 (end in-tangent) is at (index+1)*stride + 0 (start)
                        
                        val p0 = values[index * stride + componentsPerValue + i]
                        val m0 = values[index * stride + componentsPerValue * 2 + i]
                        val p1 = values[(index + 1) * stride + componentsPerValue + i]
                        val m1 = values[(index + 1) * stride + i]
                        dest[i] = Interpolator.cubicSpline(p0, m0, p1, m1, interpolationFactor, deltaTime)
                    }
                }
            }
        }, { offset ->
            for (i in 0 until componentsPerValue) {
                dest[i] = values[offset + i]
            }
        })
    }

    /**
     * Specialized sampler for [Vector3f] targets (Translation/Scale).
     * Avoids array allocations by writing directly to the Vector3f.
     *
     * @param time The time in seconds.
     * @param dest The destination Vector3f to write the result into.
     */
    fun sampleVector3f(time: Float, dest: Vector3f) {
        withSampleContext(time, 3, { index, interpolationFactor, deltaTime ->
            when (interpolation) {
                InterpolationType.STEP -> {
                    dest.set(values[index * 3], values[index * 3 + 1], values[index * 3 + 2])
                }
                InterpolationType.LINEAR -> {
                    val v0x = values[index * 3]; val v0y = values[index * 3 + 1]; val v0z = values[index * 3 + 2]
                    val v1x = values[(index + 1) * 3]; val v1y = values[(index + 1) * 3 + 1]; val v1z = values[(index + 1) * 3 + 2]
                    dest.set(v0x + (v1x - v0x) * interpolationFactor, v0y + (v1y - v0y) * interpolationFactor, v0z + (v1z - v0z) * interpolationFactor)
                }
                InterpolationType.CUBIC_SPLINE -> {
                    val stride = 9
                    val p0x = values[index * stride + 3]; val p0y = values[index * stride + 4]; val p0z = values[index * stride + 5]
                    val m0x = values[index * stride + 6]; val m0y = values[index * stride + 7]; val m0z = values[index * stride + 8]

                    val p1x = values[(index + 1) * stride + 3]; val p1y = values[(index + 1) * stride + 4]; val p1z = values[(index + 1) * stride + 5]
                    val m1x = values[(index + 1) * stride + 0]; val m1y = values[(index + 1) * stride + 1]; val m1z = values[(index + 1) * stride + 2]

                    dest.x = Interpolator.cubicSpline(p0x, m0x, p1x, m1x, interpolationFactor, deltaTime)
                    dest.y = Interpolator.cubicSpline(p0y, m0y, p1y, m1y, interpolationFactor, deltaTime)
                    dest.z = Interpolator.cubicSpline(p0z, m0z, p1z, m1z, interpolationFactor, deltaTime)
                }
            }
        }, { offset ->
            dest.set(values[offset], values[offset + 1], values[offset + 2])
        })
    }

    /**
     * Specialized sampler for [Quaternionf] targets (Rotation).
     * 
     * Note: [InterpolationType.LINEAR] interpolation for rotations in glTF is defined as SLERP (Spherical Linear Interpolation),
     * ensuring smooth rotation along the shortest arc.
     *
     * @param time The time in seconds.
     * @param dest The destination Quaternionf to write the result into.
     */
    fun sampleQuaternionf(time: Float, dest: Quaternionf) {
        withSampleContext(time, 4, { index, interpolationFactor, deltaTime ->
            when (interpolation) {
                InterpolationType.STEP -> {
                    dest.set(values[index * 4], values[index * 4 + 1], values[index * 4 + 2], values[index * 4 + 3])
                }
                InterpolationType.LINEAR -> {
                    // glTF says LINEAR for rotations means SLERP
                    val q0 = Quaternionf(values[index * 4], values[index * 4 + 1], values[index * 4 + 2], values[index * 4 + 3])
                    val q1 = Quaternionf(values[(index + 1) * 4], values[(index + 1) * 4 + 1], values[(index + 1) * 4 + 2], values[(index + 1) * 4 + 3])
                    q0.slerp(q1, interpolationFactor, dest)
                }
                InterpolationType.CUBIC_SPLINE -> {
                    val stride = 12
                    val p0x = values[index * stride + 4]; val p0y = values[index * stride + 5]; val p0z = values[index * stride + 6]; val p0w = values[index * stride + 7]
                    val m0x = values[index * stride + 8]; val m0y = values[index * stride + 9]; val m0z = values[index * stride + 10]; val m0w = values[index * stride + 11]

                    val p1x = values[(index + 1) * stride + 4]; val p1y = values[(index + 1) * stride + 5]; val p1z = values[(index + 1) * stride + 6]; val p1w = values[(index + 1) * stride + 7]
                    val m1x = values[(index + 1) * stride + 0]; val m1y = values[(index + 1) * stride + 1]; val m1z = values[(index + 1) * stride + 2]; val m1w = values[(index + 1) * stride + 3]

                    dest.x = Interpolator.cubicSpline(p0x, m0x, p1x, m1x, interpolationFactor, deltaTime)
                    dest.y = Interpolator.cubicSpline(p0y, m0y, p1y, m1y, interpolationFactor, deltaTime)
                    dest.z = Interpolator.cubicSpline(p0z, m0z, p1z, m1z, interpolationFactor, deltaTime)
                    dest.w = Interpolator.cubicSpline(p0w, m0w, p1w, m1w, interpolationFactor, deltaTime)
                    dest.normalize()
                }
            }
        }, { offset ->
            dest.set(values[offset], values[offset + 1], values[offset + 2], values[offset + 3])
        })
    }

}
