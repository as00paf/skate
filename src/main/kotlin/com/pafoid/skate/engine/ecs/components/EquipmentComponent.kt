package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.gameplay.items.ItemStack
import kotlinx.serialization.Serializable

/** What a character wears, by slot id from its skeleton config (GPL-24 §3.2); slot ids are strings, not an enum. */
@Serializable
class EquipmentComponent(
    val equipped: MutableMap<String, ItemStack> = linkedMapOf(),
) : Component()
