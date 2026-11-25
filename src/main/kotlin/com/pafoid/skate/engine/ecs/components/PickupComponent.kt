package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Serializable

/**
 * An item lying in the world (GPL-24 §5.1). Used with an [InteractableComponent] (its radius is the reach): the
 * interact press adds [count] of [itemId] to the player's inventory, or [autoPickup] takes it on contact with no
 * prompt. The object disappears once all of it fits; what does not fit stays.
 */
@Serializable
class PickupComponent(
    var itemId: String = "",
    var count: Int = 1,
    var autoPickup: Boolean = false,
) : Component()
