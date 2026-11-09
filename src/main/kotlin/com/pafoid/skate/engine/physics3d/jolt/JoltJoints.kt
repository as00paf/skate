package com.pafoid.skate.engine.physics3d.jolt

import com.github.stephengold.joltjni.FixedConstraintSettings
import com.github.stephengold.joltjni.HingeConstraintSettings
import com.github.stephengold.joltjni.SixDofConstraintSettings
import com.github.stephengold.joltjni.SliderConstraintSettings
import com.github.stephengold.joltjni.SwingTwistConstraintSettings
import com.github.stephengold.joltjni.TwoBodyConstraint
import com.github.stephengold.joltjni.TwoBodyConstraintRef
import com.github.stephengold.joltjni.TwoBodyConstraintSettings
import com.github.stephengold.joltjni.enumerate.EAxis
import com.github.stephengold.joltjni.enumerate.EConstraintSpace
import com.github.stephengold.joltjni.enumerate.ESwingType
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.engine.physics3d.constraints.ConeTwistConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.FixedConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.HingeConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.IPhysicsConstraint
import com.pafoid.skate.engine.physics3d.constraints.SixDofConstraint3D
import com.pafoid.skate.engine.physics3d.constraints.SliderConstraint3D
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/** A Jolt constraint created for one [IPhysicsConstraint] (its `rawJoint`), with the counted reference that keeps it alive. */
internal class JoltJoint(
    val constraint: TwoBodyConstraint,
    val ref: TwoBodyConstraintRef,
    val bodyA: JoltRigidBodyHandle,
    val bodyB: JoltRigidBodyHandle,
) {
    /** True while the constraint is in the physics system. */
    var added = false
}

/**
 * Builds Jolt constraints from the engine's backend-neutral descriptors, with the Bullet backend's frame conventions.
 * Descriptor pivots and axes are in each body's local (origin) frame; they are converted to world space through the
 * bodies' current poses and given to Jolt in [EConstraintSpace.WorldSpace], so no centre-of-mass correction is needed.
 */
internal object JoltJoints {
    private val X = Vector3f(1f, 0f, 0f)
    private val Y = Vector3f(0f, 1f, 0f)
    private const val PI_F = PI.toFloat()

    fun create(constraint: IPhysicsConstraint, bodyOf: (IRigidBodyHandle) -> JoltRigidBodyHandle): JoltJoint {
        val (handleA, handleB) = bodiesOf(constraint)
        val a = bodyOf(handleA)
        val b = bodyOf(handleB)
        val settings = settingsFor(constraint, Frames(a), Frames(b))
        try {
            val joint = settings.create(a.body, b.body)
            return JoltJoint(joint, joint.toRef(), a, b)
        } finally {
            settings.close()
        }
    }

    private fun bodiesOf(constraint: IPhysicsConstraint): Pair<IRigidBodyHandle, IRigidBodyHandle> = when (constraint) {
        is FixedConstraint3D -> constraint.bodyA to constraint.bodyB
        is HingeConstraint3D -> constraint.bodyA to constraint.bodyB
        is SliderConstraint3D -> constraint.bodyA to constraint.bodyB
        is ConeTwistConstraint3D -> constraint.bodyA to constraint.bodyB
        is SixDofConstraint3D -> constraint.bodyA to constraint.bodyB
        else -> throw IllegalArgumentException("Unsupported constraint ${constraint::class.simpleName}")
    }

    private fun settingsFor(constraint: IPhysicsConstraint, a: Frames, b: Frames): TwoBodyConstraintSettings = when (constraint) {
        is FixedConstraint3D -> FixedConstraintSettings().apply {
            setSpace(EConstraintSpace.WorldSpace)
            setAutoDetectPoint(false)
            a.point(constraint.pivotA).let { setPoint1(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
            b.point(constraint.pivotB).let { setPoint2(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
            // Identity frames on both bodies, like Bullet's 6-DOF fixed joint: the bodies keep the same orientation
            a.axis(X).let { setAxisX1(it.x, it.y, it.z) }
            a.axis(Y).let { setAxisY1(it.x, it.y, it.z) }
            b.axis(X).let { setAxisX2(it.x, it.y, it.z) }
            b.axis(Y).let { setAxisY2(it.x, it.y, it.z) }
        }

        is HingeConstraint3D -> HingeConstraintSettings().apply {
            setSpace(EConstraintSpace.WorldSpace)
            a.point(constraint.pivotA).let { setPoint1(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
            b.point(constraint.pivotB).let { setPoint2(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
            val (normalA, normalB) = hingeNormals(constraint.axisA, constraint.axisB)
            a.axis(constraint.axisA).let { setHingeAxis1(it.x, it.y, it.z) }
            b.axis(constraint.axisB).let { setHingeAxis2(it.x, it.y, it.z) }
            a.axis(normalA).let { setNormalAxis1(it.x, it.y, it.z) }
            b.axis(normalB).let { setNormalAxis2(it.x, it.y, it.z) }
            val (min, max) = hingeLimits(constraint.limitMin, constraint.limitMax)
            setLimitsMin(min)
            setLimitsMax(max)
        }

        is SliderConstraint3D -> SliderConstraintSettings().apply {
            setSpace(EConstraintSpace.WorldSpace)
            setAutoDetectPoint(false)
            a.point(constraint.pivotA).let { setPoint1(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
            b.point(constraint.pivotB).let { setPoint2(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
            // Bullet's slider frame maps X onto the axis; its Y axis locks rotation about the slider
            val frameA = Quaternionf().rotationTo(X, Vector3f(constraint.axisA).normalize())
            val frameB = Quaternionf().rotationTo(X, Vector3f(constraint.axisB).normalize())
            a.axis(constraint.axisA).let { setSliderAxis1(it.x, it.y, it.z) }
            b.axis(constraint.axisB).let { setSliderAxis2(it.x, it.y, it.z) }
            a.axis(frameA.transform(Vector3f(Y))).let { setNormalAxis1(it.x, it.y, it.z) }
            b.axis(frameB.transform(Vector3f(Y))).let { setNormalAxis2(it.x, it.y, it.z) }
            val (min, max) = sliderLimits(constraint.lowerLimit, constraint.upperLimit)
            setLimitsMin(min)
            setLimitsMax(max)
        }

        is ConeTwistConstraint3D -> SwingTwistConstraintSettings().apply {
            setSpace(EConstraintSpace.WorldSpace)
            a.point(constraint.pivotA).let { setPosition1(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
            b.point(constraint.pivotB).let { setPosition2(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
            // Bullet's cone twist: twist about frame X, swing span 1 about Z, swing span 2 about Y
            a.axis(column(constraint.rotA, 0)).let { setTwistAxis1(it.x, it.y, it.z) }
            a.axis(column(constraint.rotA, 1)).let { setPlaneAxis1(it.x, it.y, it.z) }
            b.axis(column(constraint.rotB, 0)).let { setTwistAxis2(it.x, it.y, it.z) }
            b.axis(column(constraint.rotB, 1)).let { setPlaneAxis2(it.x, it.y, it.z) }
            setNormalHalfConeAngle(constraint.swingSpan1.coerceIn(0f, PI_F))
            setPlaneHalfConeAngle(constraint.swingSpan2.coerceIn(0f, PI_F))
            val twist = constraint.twistSpan.coerceIn(0f, PI_F)
            setTwistMinAngle(-twist)
            setTwistMaxAngle(twist)
        }

        is SixDofConstraint3D -> SixDofConstraintSettings().apply {
            setSpace(EConstraintSpace.WorldSpace)
            a.point(constraint.pivotA).let { setPosition1(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
            b.point(constraint.pivotB).let { setPosition2(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
            a.axis(X).let { setAxisX1(it.x, it.y, it.z) }
            a.axis(Y).let { setAxisY1(it.x, it.y, it.z) }
            b.axis(X).let { setAxisX2(it.x, it.y, it.z) }
            b.axis(Y).let { setAxisY2(it.x, it.y, it.z) }
            setSwingType(ESwingType.Pyramid)
            val lin = listOf(EAxis.TranslationX, EAxis.TranslationY, EAxis.TranslationZ)
            val ang = listOf(EAxis.RotationX, EAxis.RotationY, EAxis.RotationZ)
            for (i in 0..2) {
                applyAxis(this, lin[i], constraint.linearLower[i], constraint.linearUpper[i])
                applyAxis(this, ang[i], constraint.angularLower[i], constraint.angularUpper[i], PI_F)
            }
        }

        else -> throw IllegalArgumentException("Unsupported constraint ${constraint::class.simpleName}")
    }

    /** Bullet's per-axis rule: equal limits lock the axis, lower > upper frees it, otherwise it is limited. */
    private fun applyAxis(settings: SixDofConstraintSettings, axis: EAxis, lower: Float, upper: Float, clamp: Float = Float.MAX_VALUE) {
        when (sixDofAxisMode(lower, upper)) {
            AxisMode.FIXED -> settings.makeFixedAxis(axis)
            AxisMode.FREE -> settings.makeFreeAxis(axis)
            AxisMode.LIMITED -> settings.setLimitedAxis(axis, lower.coerceIn(-clamp, clamp), upper.coerceIn(-clamp, clamp))
        }
    }

    // --- native-free rules (unit tested) ---

    enum class AxisMode { FIXED, FREE, LIMITED }

    fun sixDofAxisMode(lower: Float, upper: Float): AxisMode = when {
        lower == upper -> AxisMode.FIXED
        lower > upper -> AxisMode.FREE
        else -> AxisMode.LIMITED
    }

    /**
     * Jolt hinge limits must satisfy min ≤ 0 ≤ max within [-π, π]. Bullet's "lower > upper" means free, which is the
     * full [-π, π] range here.
     */
    fun hingeLimits(min: Float, max: Float): Pair<Float, Float> =
        if (min > max) -PI_F to PI_F
        else min.coerceIn(-PI_F, 0f) to max.coerceIn(0f, PI_F)

    /**
     * Descriptor limits bound body A's displacement relative to body B; Jolt measures body 2 (B) relative to body 1
     * (A), the opposite sign, so they are negated and swapped. Jolt needs min ≤ 0 ≤ max; lower > upper means free.
     */
    fun sliderLimits(lower: Float, upper: Float): Pair<Float, Float> =
        if (lower > upper) -Float.MAX_VALUE to Float.MAX_VALUE
        else (-upper).coerceAtMost(0f) to (-lower).coerceAtLeast(0f)

    /**
     * Bullet's hinge reference normals: a perpendicular to axis A from `btPlaneSpace1`, and the same normal rotated
     * by the shortest arc from axis A to axis B for body B. They define the zero angle the limits are measured from.
     */
    fun hingeNormals(axisA: Vector3f, axisB: Vector3f): Pair<Vector3f, Vector3f> {
        val a = Vector3f(axisA).normalize()
        val b = Vector3f(axisB).normalize()
        val normalA = planeSpace1(a)
        val normalB = Quaternionf().rotationTo(a, b).transform(Vector3f(normalA))
        return normalA to normalB
    }

    /** First vector of Bullet's `btPlaneSpace1`: a unit vector perpendicular to [n]. */
    fun planeSpace1(n: Vector3f): Vector3f =
        if (abs(n.z) > SQRT_HALF) {
            val k = 1f / sqrt(n.y * n.y + n.z * n.z)
            Vector3f(0f, -n.z * k, n.y * k)
        } else {
            val k = 1f / sqrt(n.x * n.x + n.y * n.y)
            Vector3f(-n.y * k, n.x * k, 0f)
        }

    private const val SQRT_HALF = 0.70710677f

    private fun column(m: Matrix3f, index: Int) = m.getColumn(index, Vector3f())

    /** A body's pose at constraint creation, to turn local pivots and axes into world space. */
    private class Frames(handle: JoltRigidBodyHandle) {
        private val position = handle.getLocation(Vector3f())
        private val rotation = handle.getRotation(Quaternionf())

        fun point(local: Vector3f): Vector3f = rotation.transform(Vector3f(local)).add(position)
        fun axis(local: Vector3f): Vector3f = rotation.transform(Vector3f(local)).normalize()
    }
}
