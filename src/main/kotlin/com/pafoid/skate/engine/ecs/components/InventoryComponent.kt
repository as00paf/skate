package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.gameplay.items.Inventory
import com.pafoid.skate.engine.gameplay.items.ItemStack
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Items carried by any object: a character, a chest or a vehicle (GPL-24 §3.2). Change it through
 * `InventoryRequest` events so the UI and appearance systems hear about it.
 */
@Serializable
class InventoryComponent(
    var capacity: Int = DEFAULT_CAPACITY,
    val slots: MutableList<ItemStack?> = MutableList(capacity) { null },
) : Component() {

    @Transient
    private var view: Inventory? = null

    /** The slot list as an [Inventory], resized to [capacity] first (extra slots are dropped only when empty). */
    val inventory: Inventory
        get() {
            while (slots.size < capacity) slots.add(null)
            while (slots.size > capacity && slots.last() == null) slots.removeAt(slots.size - 1)
            return view ?: Inventory(slots).also { view = it }
        }

    companion object {
        const val DEFAULT_CAPACITY = 16
    }
}
