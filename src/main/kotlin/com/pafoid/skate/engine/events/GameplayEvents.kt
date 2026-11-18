package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.gameplay.items.ItemStack
import org.joml.Vector3f

/** Gameplay events (GPL-24): requests ask a system for a change, results report what happened. */
open class GameplayEvent(eventName: String) : Event(eventName)

/** Changes to an `InventoryComponent` / `EquipmentComponent`, applied by `InventorySystem` (GPL-24 §3.3). */
sealed class InventoryRequest(eventName: String) : GameplayEvent(eventName) {
    abstract val owner: GameObject

    data class Add(override val owner: GameObject, val itemId: String, val count: Int = 1) :
        InventoryRequest("inventory.request.add")

    data class Remove(override val owner: GameObject, val slot: Int, val count: Int = 1) :
        InventoryRequest("inventory.request.remove")

    data class Move(override val owner: GameObject, val from: Int, val to: Int) :
        InventoryRequest("inventory.request.move")

    /** Equips the item in inventory [slot] into its `EquipDef.slot`; the item worn there goes back to [slot]. */
    data class Equip(override val owner: GameObject, val slot: Int) : InventoryRequest("inventory.request.equip")

    /** Puts the item worn in [slotId] back into the inventory. */
    data class Unequip(override val owner: GameObject, val slotId: String) : InventoryRequest("inventory.request.unequip")
}

/** [count] of [itemId] went into [owner]'s inventory. */
data class ItemAdded(val owner: GameObject, val itemId: String, val count: Int) : GameplayEvent("inventory.added")

/** [stack] left [owner]'s inventory (removed, or broken when [broken]). */
data class ItemRemoved(val owner: GameObject, val stack: ItemStack, val broken: Boolean = false) :
    GameplayEvent("inventory.removed")

data class ItemEquipped(val owner: GameObject, val slotId: String, val stack: ItemStack) : GameplayEvent("inventory.equipped")

data class ItemUnequipped(val owner: GameObject, val slotId: String, val stack: ItemStack) :
    GameplayEvent("inventory.unequipped")

/** [overflow] of [itemId] did not fit in [owner]'s inventory. */
data class InventoryFull(val owner: GameObject, val itemId: String, val overflow: Int) : GameplayEvent("inventory.full")

/** A request was refused; [reason] is a stable code (`unknown_item`, `not_equippable`, `no_slot`, …). */
data class InventoryRefused(val request: InventoryRequest, val reason: String) : GameplayEvent("inventory.refused")

/** [attacker] (null for the world: falls, hazards) hits [target] for [amount] of [type] damage (GPL-24 §4.2). */
data class HitEvent(
    val attacker: GameObject?,
    val target: GameObject,
    val amount: Float,
    val type: String = "physical",
    val point: Vector3f = Vector3f(),
    val normal: Vector3f = Vector3f(),
) : GameplayEvent("gameplay.hit")

/** [applied] damage got through to [target], which has [remaining] health left. */
data class DamagedEvent(val target: GameObject, val applied: Float, val remaining: Float) : GameplayEvent("gameplay.damaged")

/** [target]'s health reached 0; published once. */
data class DiedEvent(val target: GameObject, val killer: GameObject?) : GameplayEvent("gameplay.died")
