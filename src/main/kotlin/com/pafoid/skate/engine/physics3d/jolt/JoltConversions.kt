package com.pafoid.skate.engine.physics3d.jolt

import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.ln

/** Pure conversions between engine conventions (Bullet semantics) and Jolt's, kept native-free so they can be unit tested. */
internal object JoltConversions {
    /** Largest Bullet damping used for the conversion; 1 would mean infinite Jolt damping. */
    private const val MAX_DAMPING = 0.999f

    /**
     * Bullet damps velocity by `(1 - c)^dt` per step, Jolt by `1 - c·dt`. Returns the Jolt coefficient whose decay per
     * second matches Bullet's for [bulletDamping] in [0, 1], so `RigidBody3D` damping values mean the same on both.
     */
    fun damping(bulletDamping: Float): Float {
        val c = bulletDamping.coerceIn(0f, MAX_DAMPING)
        return if (c == 0f) 0f else -ln(1f - c)
    }

    /**
     * Rotation that turns Jolt's Y-aligned capsules and cylinders onto engine [axis] 0 (X), 1 (Y) or 2 (Z).
     * Returns null for Y, which needs no wrapper.
     */
    fun axisRotation(axis: Int): Quaternionf? = when (axis) {
        0 -> Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), Vector3f(1f, 0f, 0f))
        2 -> Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f))
        else -> null
    }

    /** Jolt requires a box convex radius no larger than its smallest half extent; Bullet margins are clamped to it. */
    fun boxConvexRadius(margin: Float, halfExtents: Vector3f): Float =
        margin.coerceIn(0f, minOf(halfExtents.x, halfExtents.y, halfExtents.z))

    /** Same rule for cylinders: the convex radius fits inside both the radius and the half height. */
    fun cylinderConvexRadius(margin: Float, radius: Float, halfHeight: Float): Float =
        margin.coerceIn(0f, minOf(radius, halfHeight))

    /** Jolt allowed-DOF bits (`EAllowedDofs`) for an angular factor: a zero component locks that rotation axis. */
    fun allowedDofs(angularFactor: Vector3f): Int {
        var dofs = TRANSLATION_ALL
        if (angularFactor.x != 0f) dofs = dofs or ROTATION_X
        if (angularFactor.y != 0f) dofs = dofs or ROTATION_Y
        if (angularFactor.z != 0f) dofs = dofs or ROTATION_Z
        return dofs
    }

    /** True when [angularFactor] has a component other than 0 or 1, which Jolt cannot express. */
    fun isFractional(angularFactor: Vector3f): Boolean =
        listOf(angularFactor.x, angularFactor.y, angularFactor.z).any { it != 0f && it != 1f }

    // Same values as com.github.stephengold.joltjni.enumerate.EAllowedDofs
    const val TRANSLATION_ALL = 0b000111
    const val ROTATION_X = 0b001000
    const val ROTATION_Y = 0b010000
    const val ROTATION_Z = 0b100000
    const val ALL_DOFS = 0b111111
}
