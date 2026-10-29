package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.gameplay.CharacterBehavior
import com.pafoid.skate.engine.gameplay.CharacterBehaviors
import com.pafoid.skate.engine.gameplay.LocomotionBehavior
import com.pafoid.skate.engine.gameplay.ground.GroundInfo
import com.pafoid.skate.engine.gameplay.ik.FootIkConfig
import com.pafoid.skate.engine.gameplay.ik.FootTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Marks an object as player-driven. Holds no tuning: [behaviorId] selects a registered [CharacterBehavior]
 * and [configPath] points to its standalone config file.
 */
@Serializable
class PlayerController(
    var behaviorId: String = LocomotionBehavior.ID,
    var configPath: String = "",
) : Component() {

    /** Latest ground probe, written by the physics system each fixed step. */
    @Transient
    var ground = GroundInfo()

    /** Height of a climbable step ahead, if any (from the last probe). */
    @Transient
    var stepAhead: Float? = null

    @Transient
    var wasGrounded = false

    /** Foot IK tuning and the latest per-leg ground targets (written by the physics system after each probe). */
    @Transient
    var footIk = FootIkConfig()

    @Transient
    val footTargets: List<FootTarget> = List(footIk.legs.size) { FootTarget() }

    /** Standing on walkable ground. Steep slopes count as airborne. */
    val isGrounded: Boolean get() = ground.standing

    @Transient
    var state: String = ""

    @Transient
    private var cached: CharacterBehavior? = null

    val behavior: CharacterBehavior
        get() = cached ?: CharacterBehaviors.create(behaviorId, configPath).also { cached = it }

    /** Drops the cached behavior so the config file is re-read on next use (hot reload, editor edits). */
    fun reloadConfig() {
        cached = null
    }
}
