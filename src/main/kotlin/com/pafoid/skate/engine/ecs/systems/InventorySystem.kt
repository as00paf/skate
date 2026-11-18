package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.EquipmentComponent
import com.pafoid.skate.engine.ecs.components.InventoryComponent
import com.pafoid.skate.engine.events.InventoryFull
import com.pafoid.skate.engine.events.InventoryRefused
import com.pafoid.skate.engine.events.InventoryRequest
import com.pafoid.skate.engine.events.ItemAdded
import com.pafoid.skate.engine.events.ItemEquipped
import com.pafoid.skate.engine.events.ItemRemoved
import com.pafoid.skate.engine.events.ItemUnequipped
import com.pafoid.skate.engine.gameplay.items.Inventory
import com.pafoid.skate.engine.gameplay.items.ItemDatabase
import com.pafoid.skate.engine.getComponent

/**
 * Applies [InventoryRequest] events to `InventoryComponent` / `EquipmentComponent` and publishes the results
 * (GPL-24 §3.3). Requests are applied synchronously in the subscriber, as `EventSystem.publish` is synchronous, so a
 * publisher sees the result events before `publish` returns.
 *
 * [slotsOf] lists the equip slots a character offers (from its skeleton config, GPL-24e); null accepts any slot.
 * [reload] refills [items] on each scene init (Play start, scene load), so edited `*.item.json` files are picked up.
 */
class InventorySystem(
    private val events: EventSystem,
    val items: ItemDatabase,
    var slotsOf: (GameObject) -> Set<String>? = { null },
    private val reload: ((ItemDatabase) -> Unit)? = null,
) : System() {

    override fun init(scene: Scene) {
        super.init(scene)
        reload?.let { load ->
            items.clear()
            load(items)
        }
    }

    init {
        events.subscribe<InventoryRequest.Add> { add(it) }
        events.subscribe<InventoryRequest.Remove> { remove(it) }
        events.subscribe<InventoryRequest.Move> { move(it) }
        events.subscribe<InventoryRequest.Equip> { equip(it) }
        events.subscribe<InventoryRequest.Unequip> { unequip(it) }
    }

    private fun inventoryOf(request: InventoryRequest): Inventory? {
        val inventory = request.owner.getComponent<InventoryComponent>()?.inventory
        if (inventory == null) refuse(request, NO_INVENTORY)
        return inventory
    }

    private fun refuse(request: InventoryRequest, reason: String) = events.publish(InventoryRefused(request, reason))

    private fun add(r: InventoryRequest.Add) {
        val def = items[r.itemId] ?: return refuse(r, UNKNOWN_ITEM)
        val inventory = inventoryOf(r) ?: return
        val overflow = inventory.add(def, r.count)
        val added = r.count - overflow
        if (added > 0) events.publish(ItemAdded(r.owner, def.id, added))
        if (overflow > 0) events.publish(InventoryFull(r.owner, def.id, overflow))
    }

    private fun remove(r: InventoryRequest.Remove) {
        val inventory = inventoryOf(r) ?: return
        val taken = inventory.remove(r.slot, r.count) ?: return refuse(r, EMPTY_SLOT)
        events.publish(ItemRemoved(r.owner, taken))
    }

    private fun move(r: InventoryRequest.Move) {
        val inventory = inventoryOf(r) ?: return
        inventory.move(r.from, r.to, items)
    }

    private fun equip(r: InventoryRequest.Equip) {
        val inventory = inventoryOf(r) ?: return
        val equipment = r.owner.getComponent<EquipmentComponent>() ?: return refuse(r, NO_EQUIPMENT)
        val stack = inventory.slots.getOrNull(r.slot) ?: return refuse(r, EMPTY_SLOT)
        val equip = items[stack.itemId]?.equip ?: return refuse(r, NOT_EQUIPPABLE)
        if (slotsOf(r.owner)?.contains(equip.slot) == false) return refuse(r, NO_SLOT)

        // The worn item goes back where the new one was; with a stack left there, it needs a free slot
        val old = equipment.equipped[equip.slot]
        if (old != null && stack.count > 1 && inventory.slots.none { it == null }) return refuse(r, INVENTORY_FULL)

        val worn = inventory.remove(r.slot, 1) ?: return
        if (old != null) {
            equipment.equipped.remove(equip.slot)
            if (inventory.slots[r.slot] == null) inventory.slots[r.slot] = old else inventory.put(old)
            events.publish(ItemUnequipped(r.owner, equip.slot, old))
        }
        equipment.equipped[equip.slot] = worn
        events.publish(ItemEquipped(r.owner, equip.slot, worn))
    }

    private fun unequip(r: InventoryRequest.Unequip) {
        val inventory = inventoryOf(r) ?: return
        val equipment = r.owner.getComponent<EquipmentComponent>() ?: return refuse(r, NO_EQUIPMENT)
        val worn = equipment.equipped[r.slotId] ?: return refuse(r, EMPTY_SLOT)
        if (!inventory.put(worn)) {
            events.publish(InventoryFull(r.owner, worn.itemId, worn.count))
            return
        }
        equipment.equipped.remove(r.slotId)
        events.publish(ItemUnequipped(r.owner, r.slotId, worn))
    }

    companion object {
        const val UNKNOWN_ITEM = "unknown_item"
        const val NO_INVENTORY = "no_inventory"
        const val NO_EQUIPMENT = "no_equipment"
        const val EMPTY_SLOT = "empty_slot"
        const val NOT_EQUIPPABLE = "not_equippable"
        const val NO_SLOT = "no_slot"
        const val INVENTORY_FULL = "inventory_full"
    }
}
