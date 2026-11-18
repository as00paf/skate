package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable

/**
 * Something a player can use when close (GPL-24 §5.1). [actionId] is `pickup` or any game id (`open`, `use`, …);
 * game code and scripts react to `InteractEvent`. [promptKey] is the string-table key the HUD shows while focused.
 * [requiresItem] hides it until the player carries that item. A disabled component ([enabled]) is never offered.
 */
@Serializable
class InteractableComponent(
    var actionId: String = "pickup",
    var promptKey: String = "",
    /** Reach in metres. */
    var radius: Float = 1.5f,
    var requiresItem: String = "",
) : Component()
