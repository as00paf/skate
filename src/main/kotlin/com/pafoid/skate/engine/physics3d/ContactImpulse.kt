package com.pafoid.skate.engine.physics3d

/**
 * The impulse every backend reports in `onCollisionStarted` (PHY-29a, `docs/design/PHY-29.md` §5.2): the closing speed
 * along the contact normal at contact start, from the velocities **before** the solver ran, times the pair's reduced
 * mass `1 / (1/m₁ + 1/m₂)` (a static or kinematic side has inverse mass 0). The same drop gives the same value on
 * Bullet, Jolt and `SimplePhysics3D`, so damage thresholds don't depend on the backend.
 */
object ContactImpulse {
    /**
     * [relativeNormalSpeed] is the first body's velocity minus the second's, along a normal pointing from the second
     * body to the first: below 0 means they approach. Returns 0 when they separate or neither side can move.
     */
    fun of(relativeNormalSpeed: Float, inverseMass1: Float, inverseMass2: Float): Float {
        val inverseSum = inverseMass1 + inverseMass2
        if (inverseSum <= 0f || relativeNormalSpeed >= 0f) return 0f
        return -relativeNormalSpeed / inverseSum
    }
}
