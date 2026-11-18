package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.EquipmentComponent
import com.pafoid.skate.engine.ecs.components.InventoryComponent
import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.events.InventoryFull
import com.pafoid.skate.engine.events.InventoryRefused
import com.pafoid.skate.engine.events.InventoryRequest
import com.pafoid.skate.engine.events.ItemAdded
import com.pafoid.skate.engine.events.ItemEquipped
import com.pafoid.skate.engine.events.ItemRemoved
import com.pafoid.skate.engine.events.ItemUnequipped
import com.pafoid.skate.engine.gameplay.items.EquipDef
import com.pafoid.skate.engine.gameplay.items.ItemDatabase
import com.pafoid.skate.engine.gameplay.items.ItemDef
import com.pafoid.skate.engine.gameplay.items.ItemStack
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-24a §3.4: requests in, results out, through a real [EventSystem]. */
class InventorySystemTest {
    private val events = EventSystem()
    private val db = ItemDatabase().apply {
        register(ItemDef("wax", maxStack = 5))
        register(ItemDef("cap.red", equip = EquipDef("HEAD")))
        register(ItemDef("cap.blue", equip = EquipDef("HEAD")))
        register(ItemDef("glasses", equip = EquipDef("EYES")))
        register(ItemDef("tail", equip = EquipDef("TAIL")))
    }
    private val system = InventorySystem(events, db, slotsOf = { setOf("HEAD", "EYES") })
    private val log = mutableListOf<Event>()

    init {
        events.subscribe<ItemAdded> { log += it }
        events.subscribe<ItemRemoved> { log += it }
        events.subscribe<ItemEquipped> { log += it }
        events.subscribe<ItemUnequipped> { log += it }
        events.subscribe<InventoryFull> { log += it }
        events.subscribe<InventoryRefused> { log += it }
    }

    private fun character(capacity: Int = 4) =
        GameObject("Skater").addComponent(InventoryComponent(capacity)).addComponent(EquipmentComponent())

    private val GameObject.slots get() = getComponent<InventoryComponent>()!!.inventory.slots
    private val GameObject.worn get() = getComponent<EquipmentComponent>()!!.equipped

    @Test
    fun InventorySystem_AddPublishesAddedAndOverflowPublishesFull() {
        val go = character(capacity = 1)
        events.publish(InventoryRequest.Add(go, "wax", 7))
        assertEquals(listOf(ItemAdded(go, "wax", 5), InventoryFull(go, "wax", 2)), log)
    }

    @Test
    fun InventorySystem_UnknownItemAndMissingInventoryAreRefused() {
        val go = character()
        val unknown = InventoryRequest.Add(go, "nope")
        val bare = InventoryRequest.Add(GameObject("Rock"), "wax")
        events.publish(unknown)
        events.publish(bare)
        assertEquals(listOf(InventoryRefused(unknown, InventorySystem.UNKNOWN_ITEM), InventoryRefused(bare, InventorySystem.NO_INVENTORY)), log)
    }

    @Test
    fun InventorySystem_EquipMovesTheItemIntoItsSlotAndUnequipPutsItBack() {
        val go = character()
        events.publish(InventoryRequest.Add(go, "cap.red"))
        events.publish(InventoryRequest.Equip(go, 0))
        assertNull(go.slots[0])
        assertEquals(ItemStack("cap.red", 1), go.worn["HEAD"])
        assertEquals(ItemEquipped(go, "HEAD", ItemStack("cap.red", 1)), log.last())

        events.publish(InventoryRequest.Unequip(go, "HEAD"))
        assertTrue(go.worn.isEmpty())
        assertEquals("cap.red", go.slots[0]!!.itemId)
        assertEquals(ItemUnequipped(go, "HEAD", ItemStack("cap.red", 1)), log.last())
    }

    @Test
    fun InventorySystem_EquippingIntoAnOccupiedSlotSwapsTheOldItemBack() {
        val go = character()
        events.publish(InventoryRequest.Add(go, "cap.red"))
        events.publish(InventoryRequest.Add(go, "cap.blue"))
        events.publish(InventoryRequest.Equip(go, 0))
        events.publish(InventoryRequest.Equip(go, 1))
        assertEquals("cap.blue", go.worn["HEAD"]!!.itemId)
        assertEquals("cap.red", go.slots[1]!!.itemId)
        assertEquals(listOf("HEAD"), go.worn.keys.toList())
    }

    @Test
    fun InventorySystem_HeadAndEyesAreIndependentSlots() {
        val go = character()
        events.publish(InventoryRequest.Add(go, "cap.red"))
        events.publish(InventoryRequest.Add(go, "glasses"))
        events.publish(InventoryRequest.Equip(go, 0))
        events.publish(InventoryRequest.Equip(go, 1))
        assertEquals(setOf("HEAD", "EYES"), go.worn.keys)
    }

    @Test
    fun InventorySystem_NonEquippableItemsAndUnknownSlotsAreRefused() {
        val go = character()
        events.publish(InventoryRequest.Add(go, "wax"))
        events.publish(InventoryRequest.Add(go, "tail"))
        log.clear()
        val wax = InventoryRequest.Equip(go, 0)
        val tail = InventoryRequest.Equip(go, 1)
        events.publish(wax)
        events.publish(tail)
        assertEquals(listOf(InventoryRefused(wax, InventorySystem.NOT_EQUIPPABLE), InventoryRefused(tail, InventorySystem.NO_SLOT)), log)
        assertTrue(go.worn.isEmpty())
    }

    @Test
    fun InventorySystem_UnequipWithAFullInventoryKeepsTheItemOn() {
        val go = character(capacity = 1)
        events.publish(InventoryRequest.Add(go, "cap.red"))
        events.publish(InventoryRequest.Equip(go, 0))
        events.publish(InventoryRequest.Add(go, "wax"))
        log.clear()
        events.publish(InventoryRequest.Unequip(go, "HEAD"))
        assertEquals("cap.red", go.worn["HEAD"]!!.itemId)
        assertEquals(listOf(InventoryFull(go, "cap.red", 1)), log)
    }

    @Test
    fun InventorySystem_RemoveAndMoveChangeTheSlots() {
        val go = character()
        events.publish(InventoryRequest.Add(go, "wax", 3))
        events.publish(InventoryRequest.Move(go, 0, 2))
        assertEquals(3, go.slots[2]!!.count)
        events.publish(InventoryRequest.Remove(go, 2, 2))
        assertEquals(ItemRemoved(go, ItemStack("wax", 2)), log.last())
        assertEquals(1, go.slots[2]!!.count)
    }

    @Test
    fun InventorySystem_ReloadRefillsTheItemsOnSceneInit() {
        val items = ItemDatabase().apply { register(ItemDef("stale")) }
        val reloading = InventorySystem(EventSystem(), items, reload = { it.register(ItemDef("fresh")) })
        reloading.init(Scene("s"))
        assertNull(items["stale"])
        assertEquals("fresh", items["fresh"]!!.id)
    }
}
