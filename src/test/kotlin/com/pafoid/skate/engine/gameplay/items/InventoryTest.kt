package com.pafoid.skate.engine.gameplay.items

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-24a §3.4: stacking, overflow, moves and durability on the pure inventory. */
class InventoryTest {
    private val wax = ItemDef("wax", maxStack = 5)
    private val cap = ItemDef("cap.red", equip = EquipDef("HEAD"))
    private val board = ItemDef("board", maxDurability = 10)
    private val db = ItemDatabase().apply { listOf(wax, cap, board).forEach(::register) }

    @Test
    fun Inventory_TwoAddsOfAStackableItemMergeIntoOneSlot() {
        val inv = Inventory(4)
        assertEquals(0, inv.add(wax, 2))
        assertEquals(0, inv.add(wax, 2))
        assertEquals(ItemStack("wax", 4), inv.slots[0])
        assertNull(inv.slots[1])
    }

    @Test
    fun Inventory_OverflowGoesToTheNextFreeSlot() {
        val inv = Inventory(4)
        inv.add(cap, 1)
        assertEquals(0, inv.add(wax, 7))
        assertEquals(5, inv.slots[1]!!.count)
        assertEquals(2, inv.slots[2]!!.count)
        assertEquals(7, inv.count("wax"))
    }

    @Test
    fun Inventory_FullInventoryReturnsTheOverflow() {
        val inv = Inventory(2)
        assertEquals(3, inv.add(wax, 13))
        assertEquals(10, inv.count("wax"))
        assertEquals(1, Inventory(1).add(cap, 2), "unstackable items take one slot each")
        assertEquals(0, inv.room(wax))
    }

    @Test
    fun Inventory_RemoveTakesPartOrAllOfAStack() {
        val inv = Inventory(2)
        inv.add(wax, 4)
        assertEquals(ItemStack("wax", 3), inv.remove(0, 3))
        assertEquals(1, inv.slots[0]!!.count)
        assertEquals(ItemStack("wax", 1), inv.remove(0, 5))
        assertNull(inv.slots[0])
        assertNull(inv.remove(0, 1), "empty slot")
        assertNull(inv.remove(9, 1), "invalid slot")
    }

    @Test
    fun Inventory_MoveMergesSameItemsAndSwapsOthers() {
        val inv = Inventory(3)
        inv.slots[0] = ItemStack("wax", 4)
        inv.slots[1] = ItemStack("wax", 3)
        inv.slots[2] = ItemStack("cap.red", 1)
        inv.move(0, 1, db)
        assertEquals(2, inv.slots[0]!!.count, "the rest stays where it was")
        assertEquals(5, inv.slots[1]!!.count)
        inv.move(2, 0, db)
        assertEquals("cap.red", inv.slots[0]!!.itemId)
        assertEquals("wax", inv.slots[2]!!.itemId)
    }

    @Test
    fun Inventory_WearLowersDurabilityAndRemovesTheItemAtZero() {
        val inv = Inventory(2)
        inv.add(board, 1)
        assertEquals(10, inv.slots[0]!!.durability)
        assertFalse(inv.wear(0, 4, db))
        assertEquals(6, inv.slots[0]!!.durability)
        assertTrue(inv.wear(0, 6, db))
        assertNull(inv.slots[0])
        inv.add(wax, 1)
        assertFalse(inv.wear(0, 100, db), "items without durability never wear")
    }
}
