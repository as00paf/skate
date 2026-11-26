package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.gameplay.ground.GroundConfig
import com.pafoid.skate.engine.gameplay.ground.GroundInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * What a character stands on. The serialized fields tune the probe ([GroundConfig]); `GroundProbeSystem` writes the
 * `@Transient` ones each fixed step, and `CharacterMotionSystem` and the camera read them.
 */
@Serializable
class GroundStateComponent(
    var footOffset: Float = 0f,
    var probeStartHeight: Float = 0.1f,
    var groundedDistance: Float = 0.05f,
    var snapDistance: Float = 0.3f,
    var sampleRadius: Float = 0.15f,
    var maxSlopeDeg: Float = 50f,
    var stepHeight: Float = 0.3f,
    var stepProbeDistance: Float = 0.4f,
    var maxSnapSpeed: Float = 6f,
    var snapToGround: Boolean = true,
    var stepUp: Boolean = true,
) : Component() {

    @Transient
    var ground = GroundInfo()

    /** Tag of the object under the feet (e.g. `GRINDABLE`); null when none or untagged. */
    @Transient
    var groundTag: String? = null

    /** Height of a climbable step ahead, if any. */
    @Transient
    var stepAhead: Float? = null

    @Transient
    var wasGrounded = false

    /** Standing on walkable ground. Steep slopes count as airborne. */
    val isGrounded: Boolean get() = ground.standing

    val config: GroundConfig
        get() = GroundConfig(
            footOffset, probeStartHeight, groundedDistance, snapDistance, sampleRadius, maxSlopeDeg,
            stepHeight, stepProbeDistance, maxSnapSpeed, snapToGround, stepUp,
        )

    fun apply(config: GroundConfig) {
        footOffset = config.footOffset
        probeStartHeight = config.probeStartHeight
        groundedDistance = config.groundedDistance
        snapDistance = config.snapDistance
        sampleRadius = config.sampleRadius
        maxSlopeDeg = config.maxSlopeDeg
        stepHeight = config.stepHeight
        stepProbeDistance = config.stepProbeDistance
        maxSnapSpeed = config.maxSnapSpeed
        snapToGround = config.snapToGround
        stepUp = config.stepUp
    }

    override fun reset() {
        ground = GroundInfo()
        groundTag = null
        stepAhead = null
        wasGrounded = false
    }

    companion object {
        fun of(config: GroundConfig) = GroundStateComponent().also { it.apply(config) }
    }
}
