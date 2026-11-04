package com.pafoid.skate.engine.render.particles

/**
 * Fixed-capacity structure-of-arrays storage for live particles. Live particles occupy indices `0 until count`;
 * killing one moves the last particle into its slot, so order is not stable. GL-free.
 */
class ParticlePool(capacity: Int = 0) {
    var capacity = 0
        private set
    var count = 0
        private set

    var posX = FloatArray(0); private set
    var posY = FloatArray(0); private set
    var posZ = FloatArray(0); private set
    var velX = FloatArray(0); private set
    var velY = FloatArray(0); private set
    var velZ = FloatArray(0); private set

    /** Velocity accumulated from gravity, kept apart so `endSpeedScale` only scales the launch velocity. */
    var accX = FloatArray(0); private set
    var accY = FloatArray(0); private set
    var accZ = FloatArray(0); private set
    var age = FloatArray(0); private set
    var life = FloatArray(0); private set

    init {
        ensureCapacity(capacity)
    }

    /** Resizes to exactly [newCapacity]; when shrinking, the particles past the new capacity are dropped. */
    fun ensureCapacity(newCapacity: Int) {
        val cap = newCapacity.coerceAtLeast(0)
        if (cap == capacity) return
        posX = posX.copyOf(cap); posY = posY.copyOf(cap); posZ = posZ.copyOf(cap)
        velX = velX.copyOf(cap); velY = velY.copyOf(cap); velZ = velZ.copyOf(cap)
        accX = accX.copyOf(cap); accY = accY.copyOf(cap); accZ = accZ.copyOf(cap)
        age = age.copyOf(cap); life = life.copyOf(cap)
        capacity = cap
        if (count > cap) count = cap
    }

    /** Returns the index of a new zeroed particle, or -1 when the pool is full. */
    fun spawn(): Int {
        if (count >= capacity) return -1
        val i = count++
        posX[i] = 0f; posY[i] = 0f; posZ[i] = 0f
        velX[i] = 0f; velY[i] = 0f; velZ[i] = 0f
        accX[i] = 0f; accY[i] = 0f; accZ[i] = 0f
        age[i] = 0f; life[i] = 1f
        return i
    }

    /** Removes particle [i] by moving the last live particle into its slot. */
    fun kill(i: Int) {
        val last = --count
        if (i == last) return
        posX[i] = posX[last]; posY[i] = posY[last]; posZ[i] = posZ[last]
        velX[i] = velX[last]; velY[i] = velY[last]; velZ[i] = velZ[last]
        accX[i] = accX[last]; accY[i] = accY[last]; accZ[i] = accZ[last]
        age[i] = age[last]; life[i] = life[last]
    }

    /** Age in [0, 1] of particle [i]. */
    fun normalizedAge(i: Int): Float = (age[i] / life[i]).coerceIn(0f, 1f)

    fun clear() {
        count = 0
    }
}
