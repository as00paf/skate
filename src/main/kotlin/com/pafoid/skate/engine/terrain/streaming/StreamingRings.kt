package com.pafoid.skate.engine.terrain.streaming

/**
 * Streaming rings around a focus (REN-28 §15.1): chunks within [physics] get colliders, within [detail] full data,
 * mesh and scatter; up to [far] the far field draws from the overview. A chunk unloads only past
 * [unloadFactor] × its ring radius (hysteresis). Distances are horizontal, to the chunk's box.
 */
data class StreamingRings(
    val physics: Float = 128f,
    val detail: Float = 512f,
    val far: Float = 4000f,
    val unloadFactor: Float = 1.25f,
) {
    init {
        require(physics <= detail) { "The physics ring ($physics m) must lie inside the detail ring ($detail m)" }
    }

    fun scaled(scale: Float) = copy(physics = physics * scale, detail = detail * scale, far = far * scale)

    companion object {
        /** The detail ring never shrinks below this under memory pressure (REN-28 §15.4). */
        const val MIN_DETAIL = 256f

        /**
         * Rings for a graphics quality preset (REN-28 §15.4, task 21): Low and Medium shrink the detail and far rings,
         * High and Ultra use the defaults. The physics ring never changes with quality (gameplay must not).
         */
        fun forQuality(quality: String?): StreamingRings = when (quality?.uppercase()) {
            "LOW" -> StreamingRings(detail = 256f, far = 2000f)
            "MEDIUM" -> StreamingRings(detail = 384f, far = 3000f)
            else -> StreamingRings()
        }
    }
}

/** One thing chunks stream around (REN-28 §15.3): a camera or a `StreamingFocus` entity. */
class Focus {
    var x = 0f
    var z = 0f
    var vx = 0f
    var vz = 0f
    /** False: render rings only (cameras). */
    var physics = true
    var radiusScale = 1f
    var priority = 0

    fun set(x: Float, z: Float, vx: Float = 0f, vz: Float = 0f, physics: Boolean = true, radiusScale: Float = 1f, priority: Int = 0): Focus {
        this.x = x; this.z = z; this.vx = vx; this.vz = vz; this.physics = physics; this.radiusScale = radiusScale; this.priority = priority
        return this
    }
}
