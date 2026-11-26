package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.gameplay.CharacterBehavior
import com.pafoid.skate.engine.gameplay.CharacterBehaviors
import com.pafoid.skate.engine.gameplay.LocomotionBehavior
import com.pafoid.skate.engine.gameplay.WaterResponse
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
    @AssetRef(AssetFolder.DATA) var configPath: String = "",
    /** What entering water does to this player (GPL-32): nothing, or bail then respawn. */
    var waterResponse: WaterResponse = WaterResponse.NONE,
) : Component() {

    /** Foot IK tuning and the latest per-leg ground targets (written by `GroundProbeSystem` after each probe). */
    @Transient
    var footIk = FootIkConfig()

    @Transient
    val footTargets: List<FootTarget> = List(footIk.legs.size) { FootTarget() }

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
