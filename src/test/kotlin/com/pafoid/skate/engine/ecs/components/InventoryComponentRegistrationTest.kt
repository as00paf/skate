package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.gameplay.items.ItemStack
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-24a: the inventory and equipment components are registered and keep their items through serialization. */
class InventoryComponentRegistrationTest {
    private val serializer = Serializer()

    @Test
    fun ComponentRegistry_InventoryAndEquipmentInstantiateAndMapBack() {
        assertTrue(ComponentType.INVENTORY.instantiate() is InventoryComponent)
        assertTrue(ComponentType.EQUIPMENT.instantiate() is EquipmentComponent)
        assertEquals(ComponentType.INVENTORY, InventoryComponent().type)
        assertEquals(ComponentType.EQUIPMENT, EquipmentComponent().type)
        val inventory = InventoryComponent()
        val go = GameObject("Chest").addComponent(inventory)
        assertSame(inventory, go.getComponent(ComponentType.INVENTORY))
    }

    @Test
    fun Serialization_ItemsAndEquipmentRoundTrip() {
        val inventory = InventoryComponent(3).apply { slots[1] = ItemStack("board", 1, durability = 7) }
        val equipment = EquipmentComponent().apply { equipped["HEAD"] = ItemStack("cap.red", 1) }

        val inv = serializer.decode<Component>(serializer.encode<Component>(inventory)) as InventoryComponent
        val eq = serializer.decode<Component>(serializer.encode<Component>(equipment)) as EquipmentComponent

        assertEquals(3, inv.capacity)
        assertNull(inv.slots[0])
        assertEquals(ItemStack("board", 1, 7), inv.slots[1])
        assertEquals(mapOf("HEAD" to ItemStack("cap.red", 1)), eq.equipped)
    }

    @Test
    fun InventoryComponent_CapacityChangesResizeTheSlots() {
        val c = InventoryComponent(2).apply { slots[0] = ItemStack("wax", 1) }
        c.capacity = 4
        assertEquals(4, c.inventory.capacity)
        c.capacity = 0
        assertEquals(1, c.inventory.capacity, "an occupied slot is never dropped")
    }
}
