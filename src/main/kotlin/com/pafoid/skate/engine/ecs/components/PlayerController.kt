package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.gameplay.CharacterBehavior
import com.pafoid.skate.engine.gameplay.CharacterBehaviors
import com.pafoid.skate.engine.gameplay.LocomotionBehavior
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

    @Transient
    var isGrounded = false

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
