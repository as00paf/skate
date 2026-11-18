package com.pafoid.skate.engine.gameplay

import kotlinx.serialization.Serializable

/** What a player does on PHY-16's `WaterEntered` (GPL-32). Swimming comes later as another value. */
@Serializable
enum class WaterResponse {
    /** Water does nothing to the character. */
    NONE,

    /** The character bails ([CharacterBehavior.bail]) and respawns once the bail is over. */
    BAIL,
}
